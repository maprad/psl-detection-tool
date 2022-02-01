package yesno;

import model.PreparedPSL;
import util.Filenames;
import util.PreparedPSLReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PSLYesNoGameFactory {

    private static List<PreparedPSL> preparedPSLS = null;

    /**
     *
     * @return yesno.PSLYesNoGame Object or null if an error occurred while reading the preaparedpsls
     */
    public static PSLYesNoGame getInstance() {
        if(preparedPSLS == null) {
            List<PreparedPSL> list = loadPreparedPSLs();
            if(list != null) {
                //make unmodifiable to prevent accidental changes
                preparedPSLS = Collections.unmodifiableList(list);
            } else {
                System.err.println("cannot load list");
            }
        }
        if(preparedPSLS == null) {
            System.err.println("no list loaded");
            return null;
        }
        //copy preparesPSLList:
        List<PreparedPSL> preparedPSLListCopy = new ArrayList<>(preparedPSLS.size());
        for(PreparedPSL preparedPSL: preparedPSLS) {
            preparedPSLListCopy.add(preparedPSL.copy());
        }
        return new PSLYesNoGame(preparedPSLListCopy);
    }

    private static List<PreparedPSL> loadPreparedPSLs() {
        return PreparedPSLReader.loadPreparedPSLs(Filenames.PREPARED_PSL_JSON);
    }

}
