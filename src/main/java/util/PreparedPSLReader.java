package util;

import model.PreparedPSL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PreparedPSLReader {
    /**
     * loads the prepared_psl_json file into a list of PreparedPSL objects
     * @param prepared_psl_json_file the filename/path of the json file that contains the prepared pls (produced by PSLFileReader)
     * @return null if an error occurred, a list of model.PreparedPSL objects otherwise
     */
    public static List<PreparedPSL> loadPreparedPSLs(final String prepared_psl_json_file){
        final File preparedPSLFile = new File(prepared_psl_json_file);
        if(preparedPSLFile.exists()){
            if(preparedPSLFile.canRead()) {
                try(BufferedReader reader = new BufferedReader(new FileReader(preparedPSLFile))){
                    System.out.println("read file");
                    String filecontent_json = reader.lines().collect(Collectors.joining("\n"));
                    System.out.println("parse json");
                    List<PreparedPSL> preparedPSLs = new ArrayList<>();
                    try{
                        JSONArray jsonArray = new JSONArray(filecontent_json);
                        for(int i = 0; i<jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            preparedPSLs.add(PreparedPSL.fromJSONObject(jsonObject));
                        }
                    }catch (JSONException e) {
                        e.printStackTrace();
                    }
                    System.out.println("sort list");
                    preparedPSLs.sort((o1, o2) -> {
                        final long diff = o1.getCommitTimestamp() - o2.getCommitTimestamp();
                        if(diff > 0 ) {
                            return 1;
                        } else if (diff < 0) {
                            return -1;
                        } else {
                            return 0;
                        }
                    });
                    return preparedPSLs;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.err.println("cannot read "+ prepared_psl_json_file);
            }
        }else {
            System.err.println("file "+ prepared_psl_json_file +" does not exist");
        }
        return null;
    }
}
