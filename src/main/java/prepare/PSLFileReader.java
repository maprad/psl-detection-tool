package prepare;

import model.PSL;
import model.PreparedPSL;
import org.json.JSONArray;
import util.Filenames;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class reads all PSL versions stored in the PSL_FOLDER, analyzes them, prepares "extended" lists with
 * additional information (see PreparedPSL) and stores these lists in a json file.
 */
public class PSLFileReader {

    private static final String PSL_FOLDER = Filenames.PSL_VERSIONS_FOLDER;

    private static final String OUTPUT_FILE= Filenames.PREPARED_PSL_JSON;

    final static SimpleDateFormat SDF = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    /**
     * to display progress
     */
    private static int counter = 0;
    private static int percentagePrintedCounter = 0;
    /***/

    public static void main(String[] args) {
        //prepare output file
        final File outputFile = new File(OUTPUT_FILE);
        if(outputFile.exists()) {
            if(!outputFile.canWrite()) {
                System.err.println("cannot write to output file");
                return;
            }
        } else {
            try {
                if(!outputFile.createNewFile()) {
                    System.err.println("cannot create output file: "+OUTPUT_FILE);
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("error while creating file");
                return;
            }
        }

        /**
         * read files
         */
        final File pslfolder = new File(PSL_FOLDER);
        if(pslfolder.exists()) {
            if(pslfolder.canRead()) {
                final File[] files = pslfolder.listFiles();
                final List<File> readableFiles = new ArrayList<>();
                printNewHeader("found: " + files.length+" files", false);
                for(File f: files ) {
                    if(f.canRead()) {
                        readableFiles.add(f);
                    } else {
                        System.err.println("cannot read file: " +f.getName());
                    }
                }
                if(readableFiles.size() == files.length) {
                    //all can be read: continue
                    printNewHeader("read all", true);
                    final List<PSL> pslList = new ArrayList<>(readableFiles.size());
                    readableFiles.stream().forEach(file -> {
                        try {
                            newItemProcessed(readableFiles.size());
                            pslList.add(readIntoPSL(file));
                        } catch (InvalidFilenameException e) {
                            System.err.println("invalid filename: " + file.getName());
                            return;
                        }
                    });
                    printNewHeader("sort PSLs", true);
                    //all files in list; not sort the list by commit date asc
                    pslList.sort((o1, o2) -> {
                        final long diff = o1.getCommitDate().getTime() - o2.getCommitDate().getTime();
                        if (diff > 0) {
                            return 1;
                        } else if (diff < 0) {
                            return -1;
                        } else {
                            return 0;
                        }
                    });
                    //here the list should be ordered from first to latest

                    /**
                     * count how many items are present more than once on average per PSL version
                     */
                    int total = 0;
                    for(PSL psl: pslList) {
                        total+=psl.getNumberOfEntriesThatAppearMoreThanOnce();
                    }
                    double avg = total/(pslList.size()+0.0);
                    System.out.println("AVG: "+avg +" (total: "+total+")");

                    /**
                     * check that no entry is present more than once in every psl version
                     */
                    printNewHeader("check that no entry is present more than once in every psl version", true);
                    for(PSL psl: pslList) {
                        newItemProcessed(pslList.size());
                        final HashMap<String, Boolean> map = new HashMap<>();
                        for(String entry: psl.getPslEntries()) {
                            Boolean aBoolean = map.get(entry);
                            if(aBoolean == null) {
                                map.put(entry, true);
                            } else {
                                //this entry doe not appear for the first time in this psl
                                System.err.println(entry+" appears more than once in psl "+psl.getCommitHash()+" ("+psl.getCommitDate().getTime()+")");
                                return;
                            }
                        }
                    }
                    //if reached this point no psl version contains an entry twice.

                    //moved here because otherwise exceptions that have no associated wildcard might appear in
                    // entries_without_the_ones_shared_by_all_versions or  entries_that_all_versions_have_in_common
                    // which could cause the YesNoGame to check for such an exception which cannot be done.

                    /**
                     * create exception-wildcard-mapping; remove all exception without associated wildcard
                     */
                    printNewHeader("create exception-wildcard-mapping", true);
                    final List<Map<String, String>> exceptionToWildcardMappingList = new ArrayList<>();
                    final List<List<String>> wildcardsList = new ArrayList<>();
                    {
                        for (int i = 0; i<pslList.size(); i++) {
                            newItemProcessed(pslList.size());
                            final List<String> wildcards = new ArrayList<>();
                            final List<String> exceptions = new ArrayList<>();

                            final HashSet<String> exceptionsWithoutAssociatedWildcard = new HashSet<>();

                            final Map<String, String> exceptionWildcardMapping = new HashMap<>();

                            PSL psl = pslList.get(i);
                            //find all wildcards and exceptions in this psl
                            for(String entry: psl.getPslEntries()) {
                                if(entry.startsWith("!")) {
                                    exceptions.add(entry);
                                } else if (entry.contains("*")) {
                                    wildcards.add(entry);
                                }
                            }
                            wildcardsList.add(wildcards);
                            //System.out.println("Exceptions: "+exceptions.size()+" Wildcards: "+wildcards.size());
                            //find the wildcard for every exception
                            //TODO: is it unique? is something like this possible: !a.b.foo.bar, *.b.foo.bar, *.*.foo.bar??
                            for(String exception: exceptions) {
                                boolean found = false;
                                for(String wildcard: wildcards) {
                                    if(isExceptionFromWildcard(exception, wildcard)) {
                                        //found!
                                        exceptionWildcardMapping.put(exception, wildcard);
                                        found = true;
                                        break;
                                    }
                                }
                                if(!found) {
                                    exceptionsWithoutAssociatedWildcard.add(exception);
                                    System.err.println("no wildcard found for: "+ exception +" ("+psl.getCommitHash()+")");
                                }
                            }
                            if(exceptionsWithoutAssociatedWildcard.size() > 0) {
                                //remove all exceptions that have no associated wildcards
                                psl.getPslEntries().removeIf(entry -> exceptionsWithoutAssociatedWildcard.contains(entry));
                                System.err.println(exceptionsWithoutAssociatedWildcard.size()+" exceptions without associated wildcard removed.");
                            }
                            exceptionToWildcardMappingList.add(exceptionWildcardMapping);
                        }
                    }

                    /**
                     * detect all entries that every psl versions share
                     */
                    printNewHeader("detect all entries that every psl versions share", true);
                    final List<List<String>> entries_without_the_ones_shared_by_all_versions = new ArrayList<>(pslList.size());
                    final Set<String> entries_that_all_versions_have_in_common = new HashSet<>();
                   // final HashSet<String> entriesAllVersionsHaveInCommon = new HashSet<>();
                    {
                        final Map<String, Integer> allEntriesCount = new HashMap<String, Integer>();
                        printNewHeader("\tcount how often an entry occurs", false);
                        for (PSL psl : pslList) {
                            newItemProcessed(pslList.size());
                            for (String entry : psl.getPslEntries()) {
                                Integer count = allEntriesCount.get(entry);
                                if (count == null) {
                                    //this entry is seen for the first time
                                    count = 1;
                                } else {
                                    //this entry has already been seen
                                    count += 1;
                                }
                                allEntriesCount.put(entry, count);
                            }
                        }
                        //now allEntriesCount contains the number of psl versions a certain entry appears in

                        printNewHeader("\tdetermine all entries that are part of every version", true);
                        for (String entry : allEntriesCount.keySet()) {
                            if (allEntriesCount.get(entry) == pslList.size()) {
                                entries_that_all_versions_have_in_common.add(entry);
                            }
                        }
                        //remove all entries that are part of every version of the psl from all versions of the psl

                        printNewHeader("\tbuild new list", true);
                        for (PSL psl : pslList) {
                            newItemProcessed(pslList.size());
                            entries_without_the_ones_shared_by_all_versions
                                    .add(
                                            psl.getPslEntries()
                                                    .stream()
                                                    .filter(s -> !entries_that_all_versions_have_in_common.contains(s))
                                                    .collect(Collectors.toList())
                                    );
                        }
                    }

                    /**
                     * determine equal psls
                     */
                    printNewHeader("determine equal psls", true);
                    final List<List<String>> equalList = new ArrayList<>(pslList.size());
                    {
                        //populate list for the following code
                        for (PSL psl : pslList) {
                            equalList.add(new ArrayList<>());
                        }

                        for (int i = 0; i < pslList.size(); i++) {
                            newItemProcessed(pslList.size());
                            final PSL pslI = pslList.get(i);

                            for (int j = i + 1; j < pslList.size(); j++) {
                                final PSL pslJ = pslList.get(j);
                                boolean equal = true;

                                if (pslI.getPslEntries().size() == pslJ.getPslEntries().size()) {
                                    //same size; check every entry
                                    for (String entryI : pslI.getPslEntries()) {
                                        if (!pslJ.containsEntry(entryI)) {
                                            //does not contain -> not equal
                                            equal = false;
                                            break;
                                        }
                                    }
                                    if (equal) {
                                        //pslI is euqal pslJ
                                        //add pslJ to euqallist of pslI
                                        equalList.get(i).add(pslJ.getCommitHash());
                                        //add pslI to euqallist of pslJ
                                        equalList.get(j).add(pslI.getCommitHash());
                                    }
                                } else {
                                    //cannot be equal
                                }
                            }
                        }
                    }

                    /**
                     * sort out tld entries
                     */
                    final List<Set<String>> tldEntriesList = new ArrayList<>(pslList.size());
                    final List<List<String>> pslEntriesWithoutTLDs = new ArrayList<>(pslList.size());
                    printNewHeader("sort out tld entries", true);
                    {
                        for(PSL psl: pslList) {
                            newItemProcessed(pslList.size());
                            final Set<String> tldEntriesInThisList = new HashSet<>();
                            for(String s: psl.getPslEntries()){
                                final String entryCopy = s;
                                if(s.startsWith(".")) {
                                    s = s.substring(1);
                                }
                                if(s.endsWith(".")) {
                                    s = s.substring(0,s.length()-1);
                                }
                                if(!s.contains(".")) {
                                    tldEntriesInThisList.add(entryCopy);
                                }
                            }
                            pslEntriesWithoutTLDs.add(
                                    psl.getPslEntries()
                                            .stream()
                                            .filter(s -> !tldEntriesInThisList.contains(s))
                                            .collect(Collectors.toList()));
                            //add to list
                            tldEntriesList.add(tldEntriesInThisList);
                        }

                    }

                    /**
                     * determine equals without tlds
                     */
                    final List<List<String>> equalWithoutTLDList = new ArrayList<>(pslList.size());
                    printNewHeader("determine equal psls without tld entries", true);
                    {
                        //populate list for the following code
                        for (PSL psl : pslList) {
                            equalWithoutTLDList.add(new ArrayList<>());
                        }

                        for (int i = 0; i < pslList.size(); i++) {
                            newItemProcessed(pslList.size());
                            final PSL pslI = pslList.get(i);
                            final List<String> pslIEntriesWithoutTLDs = pslEntriesWithoutTLDs.get(i);

                            for (int j = i + 1; j < pslList.size(); j++) {
                                final PSL pslJ = pslList.get(j);
                                final List<String> pslJEntriesWithoutTLDs = pslEntriesWithoutTLDs.get(j);
                                boolean equal = true;

                                if (pslIEntriesWithoutTLDs.size() ==pslJEntriesWithoutTLDs.size()) {
                                    final Set PSLJEntriesSetWithoutTLDs = new HashSet<>(pslJEntriesWithoutTLDs);
                                    //same size; check every entry
                                    for (String entryI :pslIEntriesWithoutTLDs) {
                                        if (!PSLJEntriesSetWithoutTLDs.contains(entryI)) {
                                            //does not contain -> not equal
                                            equal = false;
                                            break;
                                        }
                                    }
                                    if (equal) {
                                        //pslI is euqal pslJ
                                        //add pslJ to equalWithoutTLDList of pslI
                                        equalWithoutTLDList.get(i).add(pslJ.getCommitHash());
                                        //add pslI to equalWithoutTLDList of pslJ
                                        equalWithoutTLDList.get(j).add(pslI.getCommitHash());
                                    }
                                } else {
                                    //cannot be equal
                                }
                            }
                        }
                    }

                    /**
                     * relative changes without tlds
                     */
                    printNewHeader("determine relative changes without tlds", true);
                    final List<List<String>> relativeAdded = new ArrayList<>(pslList.size());
                    final List<List<String>> relativeRemoved = new ArrayList<>(pslList.size());
                    {
                        for (int i = pslList.size() - 1; i > 0; i--) {
                            newItemProcessed(pslList.size());
                            final PSL latest = pslList.get(i);
                            final Set<String> latestTLDs = tldEntriesList.get(i);
                            final PSL previous = pslList.get(i - 1);
                            final Set<String> previousTDLs = tldEntriesList.get(i-1);

                           // System.out.println("processing: " + SDF.format(latest.getCommitDate()));
                            //determine entries that are present in previous but are missing in latest (=removed entries)
                            final List<String> removedInLatest = previous.getPslEntries().stream()
                                    .filter(s -> !previousTDLs.contains(s) && !latest.containsEntry(s)) //no tld and not present in latest
                                    .collect(Collectors.toList());
                            //only entries that are new -> not in the previous list
                            final List<String> addedInLast = latest.getPslEntries().stream()
                                    .filter(s -> !latestTLDs.contains(s) && !previous.containsEntry(s)) //no tld and not present in previous
                                    .collect(Collectors.toList());
                            relativeAdded.add(addedInLast);
                            relativeRemoved.add(removedInLatest);
                        }
                        //add entries of first psl because this one is not part of the previous loop
                        if (pslList.size() > 0) {
                            newItemProcessed(pslList.size());
                            final PSL psl = pslList.get(0);
                            final Set pslTLDs = tldEntriesList.get(0);
                            //consider only the entries that not all have in common as added in the first version
                            //and that are not tlds
                            relativeAdded.add(psl.getPslEntries().stream().filter(s -> !pslTLDs.contains(s) && !entries_that_all_versions_have_in_common.contains(s)).collect(Collectors.toList()));
                            //no removed in first psl
                            relativeRemoved.add(new ArrayList<>());
                        }
                        //relativeAdded and relativeRemoved are ordered from latest to first.
                        Collections.reverse(relativeAdded);
                        Collections.reverse(relativeRemoved);
                        //now they should be ordered from first to latest psl
                    }

                    /**
                     * create the prepred model.PSL; build JSONArray
                     */
                    printNewHeader("build list", true);
                    final JSONArray output = new JSONArray(pslList.size());
                    for(int i = 0; i<pslList.size(); i++) {
                        newItemProcessed(pslList.size());
                        final PSL psl = pslList.get(i);
                        final PreparedPSL preparedPSL = new PreparedPSL(
                                psl.getCommitDate().getTime(),
                                psl.getCommitHash(),
                                entries_that_all_versions_have_in_common,
                                relativeAdded.get(i),
                                relativeRemoved.get(i),
                                entries_without_the_ones_shared_by_all_versions.get(i),
                                equalList.get(i),
                                tldEntriesList.get(i),
                                equalWithoutTLDList.get(i),
                                exceptionToWildcardMappingList.get(i),
                                wildcardsList.get(i));
                        output.put(preparedPSL.toJsonObject());
                    }

                    /**
                     * write to file
                     */
                    printNewHeader("write to output file", true);
                    try {
                        FileWriter fileWriter = new FileWriter(outputFile,false);
                        fileWriter.write(output.toString());
                        fileWriter.flush();
                        fileWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else{
                System.err.println("cannot read: " +PSL_FOLDER);
            }
        } else {
            System.err.println(PSL_FOLDER + " does not exist");
        }
    }


    /**
     * to display progress
     */
    private static void printNewHeader(String header, boolean printDone) {
        if(printDone) {
            System.out.println("done;");
        }
        counter = 0;
        percentagePrintedCounter = 0;
        System.out.print(header+": ");
    }

    private static void newItemProcessed(int totalItems) {
        int percent = (int)((counter/(totalItems+0.0))*100.0);
        if(percent>= percentagePrintedCounter*5) {
            System.out.print(percent+"%; ");
            percentagePrintedCounter+=1;
        }
        counter++;
    }
    /***/

    /**
     * Creates a model.PSL Object out of the psl File f. The file must be named according to the pattern <COMMIT TIMESTAMP>_<COMMIT_HASH>
     * (ideally use download.PSLDownloader.java to retrieve the file) and contain a model.PSL
     * @param f the file. It must be checked that the file is a file that can be read before calling this method.
     * @return an Object of model.PSL  - never null
     * @throws InvalidFilenameException if the file is not named correctly
     */
    private static PSL readIntoPSL(File f) throws InvalidFilenameException {
        final String filename = f.getName();
        final String[] split = filename.split("_");
        if(split.length == 2) {
            try{
                Long timestamp = Long.valueOf(split[0]);
                String hash = split[1];

                try (BufferedReader reader = new BufferedReader(new FileReader(f))){

                    String line = "";
                    final List<String> pslEntries = new ArrayList<>();
                    int numberOfEntriesThatAppearMoreThanOnce = 0;
                    final HashMap<String, Boolean> alreadyRegisteredAsDuplicateEntry = new HashMap<>();
                    final HashMap<String, Boolean> mapToPreventAddingOneEntryMoreThanOnce = new HashMap<String, Boolean>();
                    try{
                        while((line = reader.readLine()) != null) {
                            //Each line is only read up to the first whitespace;
                            int whiteSpaceIndex = -1;
                            for(int i = 0; i<line.length(); i++) {
                                if(Character.isWhitespace(line.charAt(i))){
                                    whiteSpaceIndex = i;
                                    break;
                                }
                            }
                            if(whiteSpaceIndex >= 0) {
                                line = line.substring(0, whiteSpaceIndex);
                            }
                            //upper/lowercase is not important (COM is equal com)
                            line = line.toLowerCase();
                            if(!line.startsWith("//") && !line.isBlank()){

                                //not a comment, not an empty line -> rule
                                if(mapToPreventAddingOneEntryMoreThanOnce.get(line) == null) {
                                    pslEntries.add(line);
                                    mapToPreventAddingOneEntryMoreThanOnce.put(line, true);
                                } else {
                                    if(alreadyRegisteredAsDuplicateEntry.get(line) == null) {
                                        numberOfEntriesThatAppearMoreThanOnce++;
                                        alreadyRegisteredAsDuplicateEntry.put(line, true);
                                    }
                                }

                            }
                        }
                    } catch (IOException e) {
                        // should not happen??
                        e.printStackTrace();
                    }
                    return new PSL(new Date(timestamp), hash, pslEntries, numberOfEntriesThatAppearMoreThanOnce);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        throw new InvalidFilenameException();
    }

    /**
     *
     * @param exception the exception
     * @param wildcard the wildcard
     * @return true if the exception is exception for the wildcard, false otherwise
     */
    private static boolean isExceptionFromWildcard(String exception, String wildcard) {
        //an exception must start with !
        if(exception.startsWith("!")) {
            //a wildcard must contain *
            if(wildcard.contains("*")) {
                //build String array of levels for exception and wildcard
                String[] exceptionLevels = exception.split("\\.");
                String[] wildcardLevels = wildcard.split("\\.");
                //number of levels must match
                if(exceptionLevels.length == wildcardLevels.length && exceptionLevels.length > 0) {
                    int i = 0;
                    //either the levels match 1:1 or the wildcardLevel is *
                    while(exceptionLevels[i].equals(wildcardLevels[i]) || wildcardLevels[i].equals("*")) {
                        i = i+1;
                        if(i == exceptionLevels.length){
                            //last one checked
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static class InvalidFilenameException extends Exception {

    }
}
