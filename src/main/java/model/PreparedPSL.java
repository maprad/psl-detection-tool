package model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * This class represents an "extended" PSL version. A PreparesPSL is created by the class PSLFileReader and contains
 * information that partly results from the order of the different PSL versions and partly is contained implicit in a
 * PSL version but is contained explicit in the PreparedPSL :
 * - entries added in this version
 * - entries removed in this version
 * - list of entries in this version without the entries that are present in all PSL versions
 * - list of commit Hashes od PSL version that are equal
 * - mapping exception->wildcard
 */
public class PreparedPSL {
    private long commitTimestamp;
    private String commitHash;
    private List<String> addedEntries;
    private List<String> removedEntries;
    private List<String> entries_without_the_ones_all_versions_have_in_common;
    private List<String> equalPSLs;
    private Set<String> tldEntries;
    private List<String> equalWithoutTLDEntries;
    private Map<String, String> exceptionToWildcardMapping;
    private List<String> wildcards;

    private Set<String> entries_that_all_versions_have_in_common;
    //to speed up lookups
    private HashSet<String> addedEntriesHashSet;
    private HashSet<String> removedEntriesHashSet;
    private HashSet<String> entriesWithoutSharedByAllHashSet;

    public PreparedPSL(long commitTimestamp, String commitHash, Set<String> entries_that_all_versions_have_in_common,
                       List<String> addedEntries, List<String> removedEntries,
                       List<String> entries_without_the_ones_all_versions_have_in_common, List<String> equalPSLs,
                       Set<String> tldEntries, List<String> equalWithoutTLDEntries,
                       Map<String, String> exceptionToWildcardMapping,
                       List<String> wildcards) {
        this.commitTimestamp = commitTimestamp;
        this.commitHash = commitHash;
        //make lists unmodifiable
        this.entries_that_all_versions_have_in_common = Collections.unmodifiableSet(entries_that_all_versions_have_in_common);
        this.addedEntries = Collections.unmodifiableList(addedEntries);
        this.removedEntries = Collections.unmodifiableList(removedEntries);
        this.entries_without_the_ones_all_versions_have_in_common = Collections.unmodifiableList(entries_without_the_ones_all_versions_have_in_common);
        this.equalPSLs = Collections.unmodifiableList(equalPSLs);
        this.tldEntries = Collections.unmodifiableSet(tldEntries);
        this.equalWithoutTLDEntries = Collections.unmodifiableList(equalWithoutTLDEntries);
        this.exceptionToWildcardMapping = Collections.unmodifiableMap(exceptionToWildcardMapping);
        this.wildcards = wildcards;
        //
        setAddedEntries(this.addedEntries);
        setRemovedEntries(this.removedEntries);
        entriesWithoutSharedByAllHashSet = new HashSet<>(entries_without_the_ones_all_versions_have_in_common);
    }

    public long getCommitTimestamp() {
        return commitTimestamp;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public List<String> getAddedEntries() {
        return addedEntries;
    }
    public void setAddedEntries(List<String> addedEntries) {
        this.addedEntries = addedEntries;
        addedEntriesHashSet = new HashSet<>(addedEntries);
    }

    public Set<String> getEntries_that_all_versions_have_in_common() {
        return entries_that_all_versions_have_in_common;
    }

    public boolean containsAddedEntry(Object o) {
        return addedEntriesHashSet.contains(o);
    }

    public List<String> getRemovedEntries() {
        return removedEntries;
    }
    public void setRemovedEntries(List<String> removedEntries) {
        this.removedEntries = removedEntries;
        removedEntriesHashSet = new HashSet<>(removedEntries);
    }
    public boolean containsRemovedEntry(Object o) {
        return removedEntriesHashSet.contains(o);
    }

    public boolean containsEntry(Object o) {
        boolean inEntriesWithoutSharedByALl =  entriesWithoutSharedByAllHashSet.contains(o);
        if(inEntriesWithoutSharedByALl){
            return true;
        } else {
            return entries_that_all_versions_have_in_common.contains(o);
        }
    }

    public List<String> getEntries_without_the_ones_all_versions_have_in_common() {
        return entries_without_the_ones_all_versions_have_in_common;
    }

    public List<String> getWildcards() {
        return wildcards;
    }

    public List<String> getEqualPSLs() {
        return equalPSLs;
    }

    public Set<String> getTldEntries() {
        return tldEntries;
    }

    public List<String> getEqualWithoutTLDEntries() {
        return equalWithoutTLDEntries;
    }

    public Map<String, String> getExceptionToWildcardMapping() {
        return exceptionToWildcardMapping;
    }

    public JSONObject toJsonObject(){
        JSONObject ret = new JSONObject();
        ret.put("commit_timestamp", commitTimestamp);
        ret.put("commit_hash", commitHash);
        final JSONArray entries_all_in_common = new JSONArray();
        entries_that_all_versions_have_in_common.forEach(s -> entries_all_in_common.put(s));
        ret.put("entries_that_all_versions_have_in_common", entries_all_in_common);
        final JSONArray removed = new JSONArray();
        removedEntries.forEach(s -> removed.put(s));
        ret.put("removed_entries", removed);
        final JSONArray added = new JSONArray();
        addedEntries.forEach(s -> added.put(s));
        ret.put("added_entries", added);
        final JSONArray entries_without_all_in_common = new JSONArray();
        entries_without_the_ones_all_versions_have_in_common.forEach(s -> entries_without_all_in_common.put(s));
        ret.put("entries_without_the_ones_all_versions_have_in_common", entries_without_all_in_common);
        final JSONArray equal = new JSONArray();
        equalPSLs.forEach(s -> equal.put(s));
        ret.put("equal_psls", equal);
        final JSONArray tlds  = new JSONArray();
        tldEntries.forEach(s -> tlds.put(s));
        ret.put("tlds", tlds);
        final JSONArray equalWOTLD = new JSONArray();
        equalWithoutTLDEntries.forEach(s->equalWOTLD.put(s));
        ret.put("equal_without_tlds", equalWOTLD);
        final JSONObject exceptionToWildcardJSON = new JSONObject();
        this.exceptionToWildcardMapping.forEach((s, s2) -> exceptionToWildcardJSON.put(s, s2));
        ret.put("exception_to_wildcard_mapping", exceptionToWildcardJSON);
        final JSONArray wildcards_json = new JSONArray();
        this.wildcards.forEach(s -> wildcards_json.put(s));
        ret.put("wildcards", wildcards_json);
        return ret;
    }

    public static PreparedPSL fromJSONObject(JSONObject jsonObject) {
        List<String> addedEntries = new ArrayList<>();
        List<String> removedEntries = new ArrayList<>();
        Set<String> entries_all_in_common = new HashSet<>();
        List<String> entries_without_all_in_common = new ArrayList<>();
        List<String> equal = new ArrayList<>();
        Set<String> tlds = new HashSet<>();
        List<String> equalWOTlds = new ArrayList<>();
        Map<String, String> exceptionToWildcard = new HashMap<>();
        List<String> wildcardsEntries = new ArrayList<>();
        {
            JSONArray entries_that_all_versions_have_in_common = jsonObject.getJSONArray("entries_that_all_versions_have_in_common");
            for(int i = 0; i<entries_that_all_versions_have_in_common.length(); i++) {
                entries_all_in_common.add(entries_that_all_versions_have_in_common.getString(i));
            }
        }
        {
            JSONArray jsonArrayAddedEntries = jsonObject.getJSONArray("added_entries");
            for (int i = 0; i < jsonArrayAddedEntries.length(); i++) {
                addedEntries.add(jsonArrayAddedEntries.getString(i));
            }
        }
        {
            JSONArray jsonArrayRemovedEntries = jsonObject.getJSONArray("removed_entries");
            for (int i = 0; i < jsonArrayRemovedEntries.length(); i++) {
                removedEntries.add(jsonArrayRemovedEntries.getString(i));
            }
        }
        {
            JSONArray jsonEntries = jsonObject.getJSONArray("entries_without_the_ones_all_versions_have_in_common");
            for(int i = 0; i<jsonEntries.length(); i++) {
                entries_without_all_in_common.add(jsonEntries.getString(i));
            }
        }
        {
            JSONArray jsonEqual = jsonObject.getJSONArray("equal_psls");
            for(int i = 0; i<jsonEqual.length(); i++) {
                equal.add(jsonEqual.getString(i));
            }
        }
        {
            JSONArray jsonTLDS = jsonObject.getJSONArray("tlds");
            for (int i = 0; i < jsonTLDS.length(); i++) {
                tlds.add(jsonTLDS.getString(i));
            }
        }
        {
            JSONArray jsonEqualWOTLD = jsonObject.getJSONArray("equal_without_tlds");
            for (int i = 0; i < jsonEqualWOTLD.length(); i++) {
                equalWOTlds.add(jsonEqualWOTLD.getString(i));
            }
        }
        {
            JSONObject exceptionToWildcardJSON = jsonObject.getJSONObject("exception_to_wildcard_mapping");
            for(String exception: exceptionToWildcardJSON.keySet()){
                String wildcard = exceptionToWildcardJSON.getString(exception);
                exceptionToWildcard.put(exception, wildcard);
            }
        }
        {
            JSONArray wildcards_json = jsonObject.getJSONArray("wildcards");
            for(int i = 0; i<wildcards_json.length(); i++) {
                wildcardsEntries.add(wildcards_json.getString(i));
            }
        }
        return new PreparedPSL(jsonObject.getLong("commit_timestamp"), jsonObject.getString("commit_hash"),
                entries_all_in_common, addedEntries, removedEntries, entries_without_all_in_common, equal,
                tlds, equalWOTlds, exceptionToWildcard, wildcardsEntries);

    }


    public PreparedPSL copy() {
        List<String> addedEntries = new ArrayList<>();
        List<String> removedEntries = new ArrayList<>();
        List<String> entries = new ArrayList<>();
        List<String> equal = new ArrayList<>();
        Set<String> tlds = new HashSet<>();
        List<String> equalWOTLDs = new ArrayList<>();
        Set<String> entries_all_in_common = new HashSet<>();
        Map<String, String> exceptionToWildcard = new HashMap<>();
        List<String> wildcardsCopy = new ArrayList<>();
        for(String s: getAddedEntries()) {
            addedEntries.add(s);
        }

        for(String s: getRemovedEntries()) {
            removedEntries.add(s);
        }

        for(String s: getEntries_without_the_ones_all_versions_have_in_common()) {
            entries.add(s);
        }

        for(String s: getEqualPSLs()) {
            equal.add(s);
        }

        for(String s: getTldEntries()) {
            tlds.add(s);
        }
        for(String s: getEqualWithoutTLDEntries()) {
            equalWOTLDs.add(s);
        }
        for(String s: entries_that_all_versions_have_in_common) {
            entries_all_in_common.add(s);
        }

        getExceptionToWildcardMapping().forEach((s, s2) -> exceptionToWildcard.put(s, s2));

        getWildcards().forEach(s -> wildcardsCopy.add(s));
        return new PreparedPSL(getCommitTimestamp(), getCommitHash(), entries_all_in_common, addedEntries,
                removedEntries, entries, equal,tlds, equalWOTLDs, exceptionToWildcard, wildcardsCopy);
    }


    public String toStringComplete() {
        return "PreparedPSL{" +
                "commitTimestamp=" + commitTimestamp +
                ", commitHash='" + commitHash + '\'' +
                ", entries_that_all_versions_have_in_common=" + entries_that_all_versions_have_in_common +
                ", addedEntries=" + addedEntries +
                ", removedEntries=" + removedEntries +
                ", entries_without_the_ones_all_versions_have_in_common=" + entries_without_the_ones_all_versions_have_in_common +
                ", equalPSLs=" + equalPSLs +
                ", tlds=" + tldEntries +
                ", euqal_without_tld=" + equalWithoutTLDEntries +
                ", exceptionToWildcardMapping=" + exceptionToWildcardMapping +
                ", addedEntriesHashSet=" + addedEntriesHashSet +
                ", removedEntriesHashSet=" + removedEntriesHashSet +
                ", entriesWithoutSharedByAllHashSet=" + entriesWithoutSharedByAllHashSet +
                ", wildcards=" + wildcards +
                '}';
    }

    @Override
    public String toString() {
        return "PreparedPSL{" +
                "commitHash='" + commitHash + '\'' +
                '}';
    }
}
