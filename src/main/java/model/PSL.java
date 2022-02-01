package model;

import java.util.Date;
import java.util.HashSet;
import java.util.List;

/**
 * This class represents a PSL version that is downloaded by the PSLDownloader class
 */
public class PSL {
    private Date commitDate;
    private String commitHash;
    private List<String> pslEntries;

    //to speed up lookups
    private HashSet<String> pslEntriesHashSet;

    //only because it's interesting to know
    int numberOfEntriesThatAppearMoreThanOnce = 0;
    public PSL(Date commitDate, String commitHash, List<String> pslEntries, int numberOfEntriesThatAppearMoreThanOnce) {
        this.commitDate = commitDate;
        this.commitHash = commitHash;
        this.pslEntries = pslEntries;
        this.numberOfEntriesThatAppearMoreThanOnce = numberOfEntriesThatAppearMoreThanOnce;
        pslEntriesHashSet = new HashSet<>(pslEntries);
    }

    public Date getCommitDate() {
        return commitDate;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public List<String> getPslEntries() {
        return pslEntries;
    }

    public int getNumberOfEntriesThatAppearMoreThanOnce() {
        return numberOfEntriesThatAppearMoreThanOnce;
    }

    public boolean containsEntry(Object o) {
        return pslEntriesHashSet.contains(o);
    }
}
