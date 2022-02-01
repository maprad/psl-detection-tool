package download;

import org.json.JSONArray;
import org.json.JSONException;
import util.Filenames;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is used to download all PSL Versions from GitHub
 */
public class PSLDownloader {

    private static final String OUTPUT_FOLDER = Filenames.PSL_VERSIONS_FOLDER;
    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!    REMOVE    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    private static final String GITHUB_USERNAME = null;
    private static final String GITHUB_ACCESS_TOKEN = null;
    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */

    private static final SimpleDateFormat LAST_MODIFIED_SDF = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

    public static void main(String[] args) {
        //in the beginning the psl file was "netwerk/dns/src/effective_tld_names.dat"
        int versions_of_effective_tld_names_dat = downloadAllVersions("netwerk/dns/src/effective_tld_names.dat", null);
        System.out.println("total: "+versions_of_effective_tld_names_dat);
        System.out.println("**************************************\npublic_suffix_list.dat");
        //in 2010 the file was renamed to "public_suffix_list.dat"
        int versions_of_public_suffix_list_dat = downloadAllVersions("public_suffix_list.dat", null);
        System.out.println("total: "+versions_of_public_suffix_list_dat);
    }

    /**
     * downloads all versions of the file specified in the filepath from the list repository of
     * owner publicsuffixlist on github and stores them in the OUTPUT_FOLDER
     * @param filepath
     * @param lastCommitHash the hash value of the last commit that should be downloaded
     *                       (exclusive -> means the first that will not be downloaded)
     *                       null if no such exists and all should be downloaded
     * @return the number of versions found and downloaded
     */
    private static int downloadAllVersions(String filepath, String lastCommitHash) {
        //prepare the output directory
        final File outputdir = new File(OUTPUT_FOLDER);
        outputdir.mkdirs();
        if(!outputdir.exists()) {
            System.err.println(OUTPUT_FOLDER+" cannot be created.");
            return 0;
        } else if (!outputdir.canWrite()){
            System.err.println("Cannot write to "+OUTPUT_FOLDER);
            return 0;
        }

        //identify all relevant commit hashes
        List<String> allVersionHashes = getAllVersionHashes(filepath, lastCommitHash);
        System.out.println("Found "  + allVersionHashes.size()+" versions");

        //download and store every version
        int counter = 0;
        for(String hash: allVersionHashes){
            System.out.println("version "+(counter+1)+" ("+hash+") : ");
            if(lastCommitHash != null && lastCommitHash.equals(hash)) {
                System.out.println("found lastCommitHash; return here.");
                return counter;
            }
            try {
                final Date dateForCommit = getDateForCommit(hash);
                System.out.println("\tgot date: "+dateForCommit);
                final URL url = new URL("https://raw.githubusercontent.com/publicsuffix/list/"+ hash+"/"+filepath);;
                final PageContentResult pageContent = getPageContent(url, null);
                if(pageContent != null) {
                    System.out.println("\tdownloaded");
                    final File outputfile = new File(OUTPUT_FOLDER+File.separator+buildFilename(hash, dateForCommit));
                    final FileWriter fileWriter = new FileWriter(outputfile);
                    fileWriter.write(pageContent.getContent());
                    fileWriter.flush();
                    fileWriter.close();
                    System.out.println("\twritten to " + OUTPUT_FOLDER);
                    counter++;
                } else {
                    System.err.println("Error while getting content for: " + hash);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return counter;
    }

    private static String buildFilename(String hash, Date dateForCommit){
        return dateForCommit.getTime()+"_"+hash;
    }

    /**
     * gets the date for a given commit in repo "list" of user "publixsuffix" on github.
     * @param commitSHA
     * @return
     */
    private static Date getDateForCommit(String commitSHA){
        try {
            final URL url = new URL("https://api.github.com/repos/publicsuffix/list/commits/" + commitSHA);
            PageContentResult pageContent = getPageContent(url, getGithubBasicAuth());
            if(pageContent != null) {
                String lastModifiedHeaderValue = pageContent.getLastModifiedHeaderValue();;
                if(lastModifiedHeaderValue != null) {
                    Date parse = LAST_MODIFIED_SDF.parse(lastModifiedHeaderValue);
                    return parse;
                } else {
                    System.err.println("no last modifies set for "+ commitSHA);
                }
            } else {
                System.err.println("could not read page content: "+ commitSHA);
            }
        } catch (MalformedURLException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * this method retrieves all commit hashes in repo "list" of github user "publicsuffix" that belong to a commit that
     * affects the file specified in filepath
     * @param filepath
     * @param lastCommitHash the commit that marks the end of the list of commits to download;
     *                       null if there is no such commit and all should be retreived.
     * @return a list of commit hasches - not null.
     */
    private static List<String> getAllVersionHashes(String filepath, String lastCommitHash) {
        int page = 1;
        final List<String> commitHashes = new ArrayList<>();
        int previous_page_version_hash_count = 0;
        do{
            List<String> allVersionHashesOnPage = getAllVersionHashesOnPage(filepath, page);
            if(allVersionHashesOnPage != null) {
                previous_page_version_hash_count = allVersionHashesOnPage.size();
                if(previous_page_version_hash_count > 0) {
                    commitHashes.addAll(allVersionHashesOnPage);
                }
                if(lastCommitHash != null) {
                    if (allVersionHashesOnPage.contains(lastCommitHash)) {
                        //the last page contains our last commit; return here;
                        break;
                    }
                }
            } else {
                throw new RuntimeException("an error occurred while loading page: "+page);
            }
            page+=1;
        }while (previous_page_version_hash_count > 0);

        return commitHashes;
    }

    /**
     * githubs api does not return all commits containing a filepath at once but in pages.
     * this method retrieves all commit hashes on a given page from the "list" repository of owner publicsuffix
     * on github.
     * @param filepath
     * @param page
     * @return a list of hashes in string format, null if an error occurred
     */
    private static List<String> getAllVersionHashesOnPage(String filepath, int page) {
        try {
            final URL url = new URL("https://api.github.com/repos/publicsuffix/list/commits?path="+
                    filepath
                    +"&per_page=100&page="+page);

            PageContentResult pageContent = getPageContent(url, null); //TODO: change!
            if(pageContent != null) {
                //get commit hashes
                final List<String> commitHashes = new ArrayList<>();
                try{
                    JSONArray jsonArray = new JSONArray(pageContent.getContent());
                    for(int i = 0; i<jsonArray.length(); i++) {
                        commitHashes.add(jsonArray.getJSONObject(i).getString("sha"));
                    }
                }catch (JSONException e) {
                    e.printStackTrace();
                }
                return commitHashes;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getGithubBasicAuth() {
        final String userpass = GITHUB_USERNAME + ":" + GITHUB_ACCESS_TOKEN;
        final String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
        return basicAuth;
    }

    /**
     * gets the content of the page the URL points to and the last-modified header thereof
     * @param url
     * @return a PageContentResult object, null if an error occurred
     */
    private static PageContentResult getPageContent(URL url, String basicAuth) {
        PageContentResult ret = null;
        try {
            //connect to url
            final URLConnection urlConnection = url.openConnection();

            if(basicAuth != null) {
                urlConnection.setRequestProperty("Authorization", basicAuth);
            }
            //get last-modified header
            final String last_modified_header_value = urlConnection.getHeaderField("last-modified");


            //get url content
            final InputStream inputStream = urlConnection.getInputStream();
            final String content;
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            }
            ret = new PageContentResult(last_modified_header_value, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private static class PageContentResult {
        private final String lastModifiedHeaderValue;
        private final String content;

        public PageContentResult(String lastModifiedHeaderValue, String content) {
            this.lastModifiedHeaderValue = lastModifiedHeaderValue;
            this.content = content;
        }

        public String getLastModifiedHeaderValue() {
            return lastModifiedHeaderValue;
        }

        public String getContent() {
            return content;
        }
    }

}
