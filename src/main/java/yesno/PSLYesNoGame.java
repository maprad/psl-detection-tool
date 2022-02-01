package yesno;

import model.PreparedPSL;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class PSLYesNoGame {
   // private final List<PreparedPSL> completePerparedPSLList;
    private final List<PreparedPSL> preparedPSLList;
    private final HashSet<String> alreadyUsedEntries;

    /**
     * The list and the preparedpsl objects will be altered and cannot be used anymore after passed to this constructor!!
     * @param preparedPSLList
     */
    public PSLYesNoGame(List<PreparedPSL> preparedPSLList) {
        this.preparedPSLList = preparedPSLList;
        /*
        completePerparedPSLList = new ArrayList<>(preparedPSLList.size());
        for(PreparedPSL preparedPSL: preparedPSLList) {
            completePerparedPSLList.add(preparedPSL.copy());
        }*/
        alreadyUsedEntries = new HashSet<>();
    }

    private List<PreparedPSL> getRemainingList(){
        return preparedPSLList;
    }

    public SampleResult getNextSample() {
        for(PreparedPSL pls: preparedPSLList) {
          //  System.out.println(">>"+pls.toStringComplete());
        }
        if(clearResultReady()) {
            return new SampleResult(false, true, true, null, null);
        }else if(ambiguousResultReady()) {
            return new SampleResult(false, true, false,null, null);
        } else {
            //results not ready. so there must be more than one and at least two distinct psl versions int the list
            if(preparedPSLList.size() > 0) {
                int centerIndex = (int) (preparedPSLList.size() / 2.0);
                //find a pls that is about in the center of the list and has addedEntries and/or removedEntries
                PreparedPSL preparedPSL = null;
                List<String> preparedPSL_cleaned_added_list = null;
                List<String> preparedPSL_cleaned_removed_list = null;
                boolean pslFound = false;
                for(int i = 0; i < preparedPSLList.size(); i++) { //count until preparedPSLList.size() as this is certainly an upper bound (but not the smallest)
                    if(centerIndex + i < preparedPSLList.size()) {
                        //check if the next would be ok
                        preparedPSL = preparedPSLList.get(centerIndex + i);
                        preparedPSL_cleaned_added_list = preparedPSL.getAddedEntries().stream().filter(s -> !alreadyUsedEntries.contains(s)).collect(Collectors.toList());
                        preparedPSL_cleaned_removed_list = preparedPSL.getRemovedEntries().stream().filter(s -> !alreadyUsedEntries.contains(s)).collect(Collectors.toList());
                        if(preparedPSL_cleaned_added_list.size() > 0  || preparedPSL_cleaned_removed_list.size() > 0) {
                            //the psl version "preparedPSL" can be used to determine the next sample to check.
                            pslFound = true;
                            break;
                        }
                    }
                    if(centerIndex-i >= 0) {
                        //check if previous would be ok
                        preparedPSL = preparedPSLList.get(centerIndex - i);
                        preparedPSL_cleaned_added_list = preparedPSL.getAddedEntries().stream().filter(s -> !alreadyUsedEntries.contains(s)).collect(Collectors.toList());
                        preparedPSL_cleaned_removed_list = preparedPSL.getRemovedEntries().stream().filter(s -> !alreadyUsedEntries.contains(s)).collect(Collectors.toList());
                        if(preparedPSL_cleaned_added_list.size() > 0  || preparedPSL_cleaned_removed_list.size() > 0) {
                            //the psl version "preparedPSL" can be used to determine the next sample to check.
                            pslFound = true;
                            break;
                        }
                    }
                }
                if(!pslFound) {
                    System.out.println("xxxxx");
                    System.out.println("centerIndex: " + centerIndex);
                    for(PreparedPSL pp: preparedPSLList) {
                        System.out.println(pp.toJsonObject().toString());
                        System.out.println("\t+"+String.join(",", pp.getAddedEntries()));
                        System.out.println("\t+" + String.join(",", pp.getAddedEntries().stream().filter(s -> !alreadyUsedEntries.contains(s)).collect(Collectors.toList())));

                        System.out.println("\t-"+String.join(",", pp.getRemovedEntries()));
                        System.out.println("\t-" + String.join(",", pp.getRemovedEntries().stream().filter(s -> !alreadyUsedEntries.contains(s)).collect(Collectors.toList())));
                    }

                    System.exit(0);
                } else {
                   // System.out.println("use psl: "+preparedPSL.getCommitHash());
                   // System.out.println(preparedPSL.toStringComplete());
                }
                if(preparedPSL == null) {
                    //should not happen? TODO: sure?
                    throw new RuntimeException("preparesPSL is null - why?");
                }



                //preparedPSL is the psl version (about) in the middle of the list.
                //now find the "best" entry to check for (that is one that is only present in about the half of all lists)

                //find out in how may psl versions each entry is added/removed
                HashMap<String, Integer> addedCount = new HashMap<>();
                HashMap<String, Integer> removedCount = new HashMap<>();
                for(PreparedPSL prepPsl: preparedPSLList) {
                    for(String addedEntry: prepPsl.getAddedEntries()) {
                        Integer integer = addedCount.get(addedEntry);
                        if(integer == null) {
                            addedCount.put(addedEntry,1);
                        } else {
                            addedCount.put(addedEntry, integer+1);
                        }
                    }
                    for(String removedEntry: prepPsl.getRemovedEntries()) {
                        Integer integer = removedCount.get(removedEntry);
                        if(integer == null) {
                            removedCount.put(removedEntry, 1);
                        } else {
                            removedCount.put(removedEntry, integer+1);
                        }
                    }
                }

                //find the entry of preparedPSL that is added/removed the least
                String leastAddedEntry = null;
                String leastRemovedEntry = null;
                int leastAddedEntryCount = Integer.MAX_VALUE;
                int leastRemovedEntryCount = Integer.MAX_VALUE;

                for(String addedEntry: preparedPSL_cleaned_added_list) {
                    Integer integer = addedCount.get(addedEntry);
                    if(integer<leastAddedEntryCount) {
                        leastAddedEntry = addedEntry;
                        leastAddedEntryCount = integer;
                    }
                }

                for(String removedEntry: preparedPSL_cleaned_removed_list) {
                    Integer integer = removedCount.get(removedEntry);
                    if(integer < leastRemovedEntryCount) {
                        leastRemovedEntry = removedEntry;
                        leastRemovedEntryCount = integer;
                    }
                }

                //return the entry of preparedPSL that is removed or added the least
                if(leastRemovedEntryCount<leastAddedEntryCount) {
               //     System.out.println("removed" + leastAddedEntryCount);
                    //as preparedPSL should be passed the previous version since eventual matching
                    // wildcards for exception are more likely to be in there
                  //  System.out.println("leastRemoved");
                    return new SampleResult(true, false, false, leastRemovedEntry, getPreviousPreparedPSL(preparedPSL));
                } else {
               //     System.out.println("leastAdded");
                    return new SampleResult(true, false, false, leastAddedEntry, preparedPSL);
                }

            } else { // if(pslList.size() > 0)
                System.out.println("list empty??");
                return new SampleResult(false, false, false,null, null);

            }
        }

    }

    /**
     *
     * @param preparedPSL
     * //TODO: change description? (its now using preparesPSLList instead of the completePrepatedPSLList
     * @return the PreparedPSL of the PSL version that was releases just before the one specifies in the preparedPSL
     * argument, null if there is no previous or the one specified does not exist
     */
    private PreparedPSL getPreviousPreparedPSL(PreparedPSL preparedPSL) {
        PreparedPSL previous = null;
        boolean found = false;
        for(PreparedPSL prepPSL: preparedPSLList) {
            if(prepPSL.getCommitHash().equals(preparedPSL.getCommitHash())) {
                found = true;
                break;
            }
            previous = prepPSL;
        }
        if(found) {
            return previous;
        } else {
            return null;
        }
    }
    private int getRemainingPSLs() {
        return preparedPSLList.size();
    }

    /**
     * @return true if either one list remaining or a set of equal ones
     */
    private boolean clearResultReady() {
        //if only one left
        if(preparedPSLList.size() == 1) {
            return true;
        }
        //or all remaining are equal
        if(preparedPSLList.size() > 0) {
            HashSet<String> equalPSLsBasedOnFirstEntry = new HashSet<>();
            equalPSLsBasedOnFirstEntry.add(preparedPSLList.get(0).getCommitHash());
            equalPSLsBasedOnFirstEntry.addAll(preparedPSLList.get(0).getEqualPSLs());

            if(preparedPSLList.size() == equalPSLsBasedOnFirstEntry.size()) {
                //sizes do match; check if every preparedPSL is in the equallist generated based on the first model.PSL
                // (if so this should also hold for every other than the first in the list)
                for(PreparedPSL preparedPSL: preparedPSLList) {
                    if(!equalPSLsBasedOnFirstEntry.contains(preparedPSL.getCommitHash())){
                        return false;
                    }
                }
            } else {
                //sizes do not match. so there can't be only equal lists in preparedPSLList.
                return false;
            }
        } else {
            //list is emtpy - no result ready
            return false;
        }
        return true;
    }

    /**
     * @return true if either one list remaining or a set of equal ones without considering the tlds
     */
    private boolean ambiguousResultReady() {
        //if only one left
        if(preparedPSLList.size() == 1) {
            return true;
        }
        //or all remaining are equal without considering the tlds
        if(preparedPSLList.size() > 0) {
            HashSet<String> equalPSLsWithoutTLDsBasedOnFirstEntry = new HashSet<>();
            equalPSLsWithoutTLDsBasedOnFirstEntry.add(preparedPSLList.get(0).getCommitHash());
            equalPSLsWithoutTLDsBasedOnFirstEntry.addAll(preparedPSLList.get(0).getEqualWithoutTLDEntries());

            if(preparedPSLList.size() == equalPSLsWithoutTLDsBasedOnFirstEntry.size()) {
                //sizes do match; check if every preparedPSL is in the equallist generated based on the first model.PSL
                // (if so this should also hold for every other than the first in the list)
                for(PreparedPSL preparedPSL: preparedPSLList) {
                    if(!equalPSLsWithoutTLDsBasedOnFirstEntry.contains(preparedPSL.getCommitHash())){
                        return false;
                    }
                }
            } else {
                //sizes do not match. so there can't be only equal lists in preparedPSLList.
                return false;
            }
        } else {
            //list is emtpy - no result ready
            return false;
        }
        return true;
    }


    public void setResult(String sample, boolean isInTheSearchedList) {
        alreadyUsedEntries.add(sample);
        if(isInStrictMode()) {
            preparedPSLList.removeIf(preparedPSL -> preparedPSL.containsEntry(sample) != isInTheSearchedList);
        } else {
            preparedPSLList.removeIf(preparedPSL -> {
                if(preparedPSL.containsEntry(sample) != isInTheSearchedList) {
                    //in strict mode it would be removed; check if this is justified in non-strict mode too
                    if(isInTheSearchedList){
                        //we only care if isInTheSearchedList is true because if it is false every version that contains
                        //the entry would be removed. We do not care because if we are less strict in this case we would
                        //only remove more versions which is not necessary. With lessStrictMode we want to ensure that
                        //we do not remove versions that could be possible.
                        if(preparedPSL.containsEntry("*."+sample)){
                            //*.sample is in the list. we do not remove this version
                            return false;
                        } else {
                            //check if maybe there is a wildcard entry in this version that matches the sample
                            for(String wildcard: preparedPSL.getWildcards()) {
                                if(wildcardMatchesDomain(wildcard,sample)) {
                                    return false;
                                }
                            }
                            //if this point is reached there is no wildcard that matches the sample.
                            //remove this version
                            return true;
                        }
                    } else {
                        return true;
                    }
                } else {
                    return false;
                }
            });
        }
        //System.out.print("remaining psls: " + preparedPSLList.size());
        determineRelativeChanges();
    }

    private boolean wildcardMatchesDomain(String wildcard, String domain) {
        if(!wildcard.contains("*")) {
            //not a wildcard
            return false;
        }

        String[] wildcardSplit = wildcard.split("\\.");
        String[] domainSplit = domain.split("\\.");

        if(wildcardSplit.length != domainSplit.length) {
            //levels do not match -> wildcard does not match domain
            return false;
        }
        //here the levels are equals
        for(int i = 0;i <wildcardSplit.length; i++) {
            if(!wildcardSplit[i].equals(domainSplit[i]) && !wildcardSplit[i].equals("*")) {
                //if the two levels do not match and at this level there is no wildcard character the wildcard does not match the domain
                return false;
            }
        }
        //at ths point they must match
        return true;
    }

    private void determineRelativeChanges(){
        /** determine relative changes**/
        {
            for (int i = preparedPSLList.size() - 1; i > 0; i--) {
                final PreparedPSL latest = preparedPSLList.get(i);
                final PreparedPSL previous = preparedPSLList.get(i - 1);

                //determine entries that are present in previous but are missing in latest (=removed entries)
                final List<String> removedInLatest = previous.getEntries_without_the_ones_all_versions_have_in_common().stream()
                        .filter(s -> !previous.getTldEntries().contains(s) && !latest.containsEntry(s)) //not a tld and not present in latest
                        .collect(Collectors.toList());
                //only entries that are new -> not in the previous list
                final List<String> addedInLast = latest.getEntries_without_the_ones_all_versions_have_in_common().stream()
                        .filter(s -> !latest.getTldEntries().contains(s) && !previous.containsEntry(s)) //not a tld and not present in previous
                        .collect(Collectors.toList());
                latest.setAddedEntries(addedInLast);
                latest.setRemovedEntries(removedInLatest);
            }
            //add entries of first psl because this one is not part of the previous loop
            if (preparedPSLList.size() > 0) {
                final PreparedPSL psl = preparedPSLList.get(0);
                psl.setAddedEntries(new ArrayList<>());
                psl.setRemovedEntries(new ArrayList<>());
            }
        }
    }
    /**
     * in strict mode the entries must match 1:1 in order to be considered equal.
     * If strict mode is false entries like foo.bar and *.bar or *.foo.bar are considered as equals too.
     * @return
     */
    boolean isInStrictMode(){
        return false;
    }

    public static class SampleResult {
        private boolean isSample;
        private boolean resultReady;
        private boolean isClearResult;
        private String sample;
        private PreparedPSL preparedPSL;

        public SampleResult(boolean isSample, boolean resultReady, boolean isClearResult,
                            String sample, PreparedPSL preparedPSL) {
            this.isSample = isSample;
            this.resultReady = resultReady;
            this.isClearResult = isClearResult;
            this.sample = sample;
            this.preparedPSL = preparedPSL;
        }

        public boolean isSample() {
            return isSample;
        }

        public boolean isResultReady() {
            return resultReady;
        }

        public boolean isClearResult() {
            return isClearResult;
        }

        public String getSample() {
            return sample;
        }

        public String getWildcardForException(String exception) {
            if(preparedPSL != null) {
                return preparedPSL.getExceptionToWildcardMapping().get(exception);
            }
            return null;
        }

        public String getVersionThatContainsCurrentSample() {
            return preparedPSL.getCommitHash();
        }
    }


    public static void main(String[] args) {
        PSLYesNoGame instance = PSLYesNoGameFactory.getInstance();
        SampleResult nextSample = null;

        Scanner scanner = new Scanner(System.in);
        do{
            nextSample = instance.getNextSample();
            if(nextSample.isSample()) {
                System.out.println("["+instance.getRemainingPSLs()+"] sample: "+ nextSample.sample);
                String choice = "";
                do {
                    System.out.print("\tcontains? [y/n]");
                    choice = scanner.nextLine();
                } while(!choice.equals("n") && !choice.equals("y"));

                if(choice.equals("n")) {
                    instance.setResult(nextSample.sample, false);
                }else if (choice.equals("y")) {
                    instance.setResult(nextSample.sample, true);
                }
            }else if(!nextSample.isSample && !nextSample.isResultReady()) {
                System.out.println("error");
                break;
            }

        }while(!nextSample.resultReady);
        System.out.println("result: ");
        instance.printResults();
    }

    public List<PreparedPSL> getResult() {
        return this.preparedPSLList;
    }
    private void printResults() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        for(PreparedPSL preparedPSL: preparedPSLList) {
            System.out.println(preparedPSL.getCommitHash() + ": "+sdf.format(new Date(preparedPSL.getCommitTimestamp())));
        }
    }
}
