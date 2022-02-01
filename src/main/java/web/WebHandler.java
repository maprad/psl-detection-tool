package web;

import model.PreparedPSL;
import spark.Request;
import spark.Response;
import yesno.PSLYesNoGame;
import yesno.PSLYesNoGameFactory;

import java.net.IDN;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class WebHandler {

    private static final String COOKIE_KEY = "psltestcookie1h37dngz";
    private static final int COOKIE_VALUE_LENGTH = 10;
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd.MM.YYYY HH:mm:ss");

    //queue of psl entries that should be checked next. necessary because when checking an exception two check should be
    //preformed. fist the associated wildcard, then the exception itself
    private static final Queue<String> nextCheckQueue = new LinkedList<String>();

    //map to keep track of the cookie values set for domains
    private static final HashMap<String, String> issuedCookiesForDomain = new HashMap<>();

    //map to keep track of which wildcard is checked with the random domain (e.g ABsd82jg02.foo.bar -> *.foo.bar)
    private static final HashMap<String, String> wildcardDomainCheck = new HashMap<>();

    //set to remeber which domains are checked because they appear in the psl as exception.
    //e.g. !foo.bar could be the entry but the check must be doe foo.bar. The result has do be interpreted differently
    //depending on whether the list contains !foo.bar or foo.bar
    private static final HashSet<String> inListAsException = new HashSet<>();

    private static PSLYesNoGame yesNoGameInstance = PSLYesNoGameFactory.getInstance();

    private static PreparedPSL lastResult = null;
    private static String messages = "";

    public static void main(String[] args) {
        port(8080);
        before((request, response) -> {
            System.out.println("Request-Host: " + request.url());
        });
        get("/", WebHandler::handleRequest);
    }

    private static String handleRequest(Request request, Response response) {
        String host = request.host();
        host = IDN.toUnicode(host);
        String cookies = "";
        if (request.cookies() != null) {
            cookies = request.cookies().keySet().stream().collect(Collectors.joining(","));
        }
        System.out.println(host + " (" + cookies + ")");

        boolean boh = false;

        if (host.startsWith("setcookie")) {
            String domain = stripHostHeader(host);
            System.out.println("STRIP: " + domain);
            String cookievalue = getRandomString(COOKIE_VALUE_LENGTH);
            setCookie(domain, response, COOKIE_KEY, cookievalue);
            issuedCookiesForDomain.put(domain, cookievalue);
            String nextAddress = "readcookie." + domain;
            return buildResponse(buildSiteForNextAddress(nextAddress));
        } else if (host.startsWith("readcookie")) {
            String cookie = request.cookie(COOKIE_KEY);
            String domain = stripHostHeader(host);
            String issuedCookie = issuedCookiesForDomain.get(domain);
            if (issuedCookie != null) {
                if (cookie != null) {
                    if (issuedCookie.equals(cookie)) {
                        //match; got cookie -> domain is not in PSL
                        //(in case that an exception is checked -> the exception is in the browsers psl)
                        System.out.println("\tgot cookie");
                        if (inListAsException.contains(domain.toLowerCase())) {
                            inListAsException.remove(domain.toLowerCase());
                            //!domain is in browsers psl
                            domain = "!"+domain;
                            setCookieCheckResult(domain, true);
                        } else if (wildcardDomainCheck.containsKey(domain)) {
                            //if the wildcard Check was performed because it should be checked for an exception
                            // in the next step the exception check should be omitted if the wildcard is not in list
                            // (the check for exception cannot be done as if the wildcard rule is not present there can
                            // be no exception)
                            String nextCheckPeek = nextCheckQueue.peek();
                            if(nextCheckPeek != null && nextCheckPeek.startsWith("!")) {
                                //remove exception check
                                nextCheckQueue.poll();
                            }
                            //
                            setCookieCheckResult(wildcardDomainCheck.get(domain.toLowerCase()), false);
                        } else {
                            //no exception, no wildcard -> normal entry
                            setCookieCheckResult(domain, false);
                        }
                    } else {
                        //got but do not match??
                        System.err.println("cookies do not match");
                    }
                } else {
                    //got no cookie but was issued -> domain is in PSL (in case that an exception is checked:
                    //domain is treated as PSL by browser -> not listed as exception in browser list)

                    System.out.println("\tgot no cookie but it was issued");

                    if (inListAsException.contains(domain.toLowerCase())) {
                        inListAsException.remove(domain.toLowerCase());
                        //!domain not in browsers psl
                        domain = "!"+domain;
                        setCookieCheckResult(domain, false);
                    } else if (wildcardDomainCheck.containsKey(domain)) {
                        setCookieCheckResult(wildcardDomainCheck.get(domain.toLowerCase()), true);
                    } else {
                        setCookieCheckResult(domain, true);
                    }
                }

            } else {
                System.err.println("no cookie previously issued for this domain");
            }

        } else if (host.equals("startpsl.test")) {
            System.out.println("new instance");
            clearAll();
            yesNoGameInstance = PSLYesNoGameFactory.getInstance();
        } else {
            System.out.println("boh");
            boh = true;
        }

        if (!boh) {
            if(!isInVerifyMode()) {
                //only get the next sample from yesNoGame if every other check is already performed.
                if (nextCheckQueue.isEmpty()) {
                    PSLYesNoGame.SampleResult nextSample = yesNoGameInstance.getNextSample();
                    if (nextSample.isSample()) {
                        String sample = nextSample.getSample();
                        System.out.println("\tsample: " + sample);

                        if (sample.startsWith("!")) {
                            //exception
                            String wildcard = nextSample.getWildcardForException(sample);
                            if (wildcard != null) {
                                //check first for wildcard, then for exception (=nextCheckQueue.add(sample); end of outer if block)
                                nextCheckQueue.add(wildcard);
                            } else {
                                System.err.println("no wildcard found for: " + sample);
                                return "no wildcard for exception found: " + sample;
                            }
                        }
                        nextCheckQueue.add(sample);
                    } else if (nextSample.isResultReady()) {
                        List<PreparedPSL> result = yesNoGameInstance.getResult();
                        String bodycontent = "";
                        if(nextSample.isClearResult() && result.size() > 1) {
                            bodycontent += "The following PSL versions are EQUAL in terms of entries:<br>";
                        } else if(!nextSample.isClearResult()){
                            bodycontent+="The following PSL versions DIFFER in TLD entries which unfortunately cannot be checked:<br>";
                        }
                        for (PreparedPSL preparedPSL : result) {
                            bodycontent = bodycontent + preparedPSL.getCommitHash() + " (" + SDF.format(new Date(preparedPSL.getCommitTimestamp())) + ")\n<br>\n";
                        }
                        addMessageToResponse("Result:<br>"+bodycontent);
                        resultFound(result.get(0));
                      //  return buildResponse("-");
                    } else {
                        //error
                        System.err.println("error");
                        return "error";
                    }
                }
            } else {
                //in verify mode
                if(nextCheckQueue.isEmpty()) {
                    //done;
                    return buildResponse("done!");
                }
            }

            /**
             * process next queue element
             */
            {
                String sample = nextCheckQueue.poll();
                if (sample == null) {
                    System.err.println("error. queue is empty");
                    return "error: empty queue";
                }

                if (sample.startsWith("!")) {
                    sample = sample.substring(1);
                    inListAsException.add(sample.toLowerCase());
                } else if (sample.contains("*")) {
                    //contains wildcard
                    String withoutWildcard = sample;
                    while (withoutWildcard.contains("*")) {
                        String wildcardValue = getRandomString(10);
                        int index = withoutWildcard.indexOf("*");
                        String res = "";
                        for (int i = 0; i < withoutWildcard.length(); i++) {
                            if (i == index) {
                                res = res + wildcardValue;
                            } else {
                                res = res + withoutWildcard.charAt(i);
                            }
                        }
                        withoutWildcard = res;
                    }
                    wildcardDomainCheck.put(withoutWildcard.toLowerCase(), sample);
                    sample = withoutWildcard;
                }

                System.out.println("\tcheckfor: " + sample);
                String nextAddress = "setcookie." + sample;

                return buildResponse(buildSiteForNextAddress(nextAddress));
            }

        } else {
            //setCookie(stripHostHeader(host), response, COOKIE_KEY, getRandomString(COOKIE_VALUE_LENGTH));
            return buildResponse("Set up for PSL Test. Go to <a href=\"http://startpsl.test\">http://startpsl.test</a> to start the test.");
        }
    }

    private static String buildSiteForNextAddress(String nextAddress) {
        return "<a href=\"http://" + nextAddress + "\">" + nextAddress + "</a><script>setTimeout(function(){ window.location.href = \"http://" + nextAddress + "\"; }, 1);</script>";
    }


    private static void clearAll(){
        issuedCookiesForDomain.clear();
        wildcardDomainCheck.clear();
        nextCheckQueue.clear();
        inListAsException.clear();
        messages = "";
        lastResult = null;
    }
    private static boolean isInVerifyMode()  {
        return lastResult != null;
    }
    private static void resultFound(PreparedPSL preparedPSL) {
        System.out.println("Result Found: " + preparedPSL.getCommitHash());
        //preserve messages;
        String messages_copy = messages;
        clearAll();
        messages = messages_copy;

        lastResult = preparedPSL;

        addMessageToResponse("Check every entry in "+preparedPSL.getCommitHash()+ " (" + SDF.format(new Date(preparedPSL.getCommitTimestamp())) + ") to verify the result");

        Consumer<String> addToNextCheckQueueConsumer = sample -> {
            if (sample.startsWith("!")) {
                //exception
                String wildcard = preparedPSL.getExceptionToWildcardMapping().get(sample);
                if (wildcard != null) {
                    //check first for wildcard, then for exception (=nextCheckQueue.add(sample); end of outer if block)
                    nextCheckQueue.add(wildcard);
                } else {
                    System.err.println("no wildcard found for: " + sample);
                    throw new RuntimeException("no wildcard for exception found: " + sample);
                }
            }
            nextCheckQueue.add(sample);
        };

        //add all to nextCheckQueue
        preparedPSL.getEntries_without_the_ones_all_versions_have_in_common().stream().forEach(addToNextCheckQueueConsumer);
        preparedPSL.getEntries_that_all_versions_have_in_common().stream().forEach(addToNextCheckQueueConsumer);
    }


    private static void setCookieCheckResult(String domain, boolean isInPSL){
        if(!isInVerifyMode()) {
            System.out.println("\tsetResult(" + domain + ", " + isInPSL + ")");
            yesNoGameInstance.setResult(domain, isInPSL);
        } else {
            if(!isInPSL) {
                addMessageToResponse(domain+ " is not in the PSL!!!");
            }
        }
    }

    private static String buildResponse(String bodycontent){
        String additionalInformation = "";
        if(isInVerifyMode()) {
            additionalInformation = "<br>"+nextCheckQueue.size() +" left";
        }
        return "<html>" +
                    "<head>" +
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                    "</head>" +
                    "<body>" +
                        bodycontent +
                        additionalInformation +
                        "<hr>" +
                        messages +
                    "</body>" +
                "</html>";
    }

    private static void addMessageToResponse(String message) {
        messages+=message+"<br>";
    }

    private static String stripHostHeader(String host) {
        //remove setcookie subdomain if present
        String domain = host.replace("setcookie.", "");
        //remove readcookie subdomain if present
        domain = domain.replace("readcookie.", "");
        //remove port
        if (domain.contains(":")) {
            domain = domain.substring(0, domain.indexOf(":"));
        }
        return domain;
    }

    private static void setCookie(String domain, Response response, String key, String value) {
        //set cookie for remaining host
        // response.header("Set-Cookie",key+"="+value+"; SameSite=Lax; max-age=31536000; domain="+domain);
        response.header("Set-Cookie", key + "=" + value + "; SameSite=Lax; max-age=31536000; domain=" + domain);
    }

    private static String getRandomString(int size) {
        String ret = "";
        for (int i = 0; i < size; i++) {
            ret = ret + ALPHANUM.charAt((int) (Math.random() * ALPHANUM.length()));
        }
        return ret;
    }

}
