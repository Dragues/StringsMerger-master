
import com.sun.org.apache.xerces.internal.dom.DeferredElementImpl;
import javafx.util.Pair;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Chistyakov on 16.06.17.
 */

public class Main {

    public static String NOT_TRANSLATED_TAG = "<!--not translated-->";
    public static int countUpdated = 0;
    public static List<Pair<String,String>> updated = new ArrayList<>();
    public static List<Pair<String,String>> added = new ArrayList<>();
    public static HashMap<String,String> attributeMap = new HashMap<>(); // map for all keys of strings

    public static void main (String [] args) throws Exception {

        if (args.length < 2)
            throw new Exception("You must write filenames. old=xxx new=yyy");

        // Filenames
        String oldName = args[0].split("=")[1];
        String newName = args[1].split("=")[1];

        System.out.println("----------------------------");
        System.out.println("Start strings migration...");

        System.out.println("Start info...");
        StringBuilder finalData = getData(oldName);
        HashMap<String,String> oldMapTranslated = getMap(oldName, true);
        System.out.println("Translated "+ oldMapTranslated.size());
        HashMap<String,String> oldMapNotTranslated = getMap(oldName, false);
        System.out.println("Not Translated "+ oldMapNotTranslated.size());
        HashMap<String,String> newMap = getMap(newName, true);

        // Starting migration process
        for(Map.Entry<String, String> entry : newMap.entrySet()) {
            if(oldMapTranslated.containsKey(entry.getKey()) && !oldMapTranslated.get(entry.getKey()).equals(entry.getValue())) {
                countUpdated++;
                updated.add(new Pair<>(entry.getKey(),entry.getValue()));
            }
            else if (!oldMapTranslated.containsKey(entry.getKey())) {
                added.add(new Pair<>(entry.getKey(),entry.getValue()));
            }
            oldMapTranslated.put(entry.getKey(), entry.getValue());
            if (oldMapNotTranslated.containsKey(entry.getKey()))
                oldMapNotTranslated.remove(entry.getKey());
        }

        // Sort results
        Comparator comp = new Comparator<Pair<String, String>>() {
            public int compare(Pair<String, String> o1, Pair<String, String> o2) {
                return  o1.getKey().compareTo(o2.getKey());
            }
        };
        List<Pair<String, String>> resultTranslated = new ArrayList<>();
        for(Map.Entry<String, String> entry : oldMapTranslated.entrySet()) {
            resultTranslated.add(new Pair<>(entry.getKey(), entry.getValue()));
        }
        Collections.sort(resultTranslated, comp);
        List<Pair<String, String>> resultNotTranslated = new ArrayList<>();
        for(Map.Entry<String, String> entry : oldMapNotTranslated.entrySet()) {
            resultNotTranslated.add(new Pair<>(entry.getKey(), entry.getValue()));
        }
        Collections.sort(resultNotTranslated, comp);

        // Printing results
        System.out.println("Result info");
        System.out.println("----------------------------");
        System.out.println("Translated "+ resultTranslated.size());
        System.out.println("----------------------------");
        System.out.println("Not translated "+ resultNotTranslated.size());
        StringBuilder builder = buildResult(finalData.toString(), resultTranslated, resultNotTranslated);
        writeDataToFile(builder);

        // print Details
        printDetails();
    }

    private static void printDetails() {
        System.out.println("Detail info");
        System.out.println("----------------------------");
        System.out.println("Updated");
        for (Pair s : updated)
            System.out.println("<string " + attributeMap.get(s.getKey()) + ">" +  s.getValue() + "</string>");
        System.out.println("----------------------------");
        System.out.println("Added");
        for (Pair s : added)
            System.out.println("<string " + attributeMap.get(s.getKey()) + ">" +  s.getValue() + "</string>");
    }

    private static StringBuilder getData(String filename) throws Exception {
        String fileContent = readFile(filename, Arrays.asList(".*xml version.*", ".*<string.*name.*</string>", ".*resources>.*"));
        return new StringBuilder(fileContent);
    }

    private static StringBuilder buildResult(String constData, List<Pair<String, String>> resultTranslated, List<Pair<String, String>> resultNotTranslated) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "\n");
        builder.append("<resources>" + "\n");
        for (int i = 0; i < resultTranslated.size() ; i++) {
            builder.append(" <string " + attributeMap.get(resultTranslated.get(i).getKey()) + ">" +  resultTranslated.get(i).getValue() + "</string>" + "\n");
        }
        builder.append(constData);
        builder.append(NOT_TRANSLATED_TAG + "\n");
        for (int i = 0; i < resultNotTranslated.size() ; i++) {
            builder.append(" <string " + attributeMap.get(resultNotTranslated.get(i).getKey()) + ">" +  resultNotTranslated.get(i).getValue() + "</string>" + "\n");
        }
        builder.append("</resources>");
        return builder;
    }

    private static void writeDataToFile(StringBuilder builder) throws Exception {
        File file = new File( "result" + ".xml");
        if (file.exists())
            file.delete();
        file.createNewFile();

        StringBuffer sb = new StringBuffer(builder.toString());
        try{
            FileWriter fwriter = new FileWriter(file);
            BufferedWriter bwriter = new BufferedWriter(fwriter);
            bwriter.write(sb.toString());
            bwriter.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private static HashMap<String,String> getMap(String fileName, boolean translated) throws Exception {
        File file = new File(fileName + ".xml");
        if (!file.exists())
            throw new Exception("No file with name " + fileName);

        String fileContent = readFile(fileName, Arrays.asList(".*xml version.*"));
        if (fileContent.contains(NOT_TRANSLATED_TAG)) {
            return convertXmlToMap(fileContent.split(NOT_TRANSLATED_TAG)[translated ? 0 : 1], true);
        }
        else {
            return translated ? convertXmlToMap(fileContent, false) : new HashMap<>();
        }
    }

    private static HashMap<String,String> convertXmlToMap(String source, boolean afterSplit) {
        if (afterSplit) {
            if (source.contains("<resources>"))
                source = source + "\n" + "</resources>";
            else if (source.contains("</resources>"))
                source = "<resources>" + "\n" + source;
        }
        HashMap<String, String> map = new HashMap<>();
        try {
            DocumentBuilderFactory dbFactory
                    = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.parse(new InputSource(new StringReader(source)));
            doc.getDocumentElement().normalize();
            //System.out.println("Root element :"
            //        + doc.getDocumentElement().getNodeName());
            NodeList nList = doc.getElementsByTagName("string");
            System.out.println("----------------------------");
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                String strName = ((DeferredElementImpl) nNode).getAttribute("name");

                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    String strValue = eElement.getTextContent();
                    map.put(strName, strValue);
                    String attrString = "";
                    for (int i =0; i < ((DeferredElementImpl) nNode).getAttributes().getLength(); i++) {
                        attrString += ((DeferredElementImpl) nNode).getAttributes().item(i).toString() + " ";
                    }
                    attributeMap.put(strName, attrString);
                    //System.out.println("Name = " + strName +"\t" + "Value = " + strValue);
                }
            }
        } catch (Exception e) {
            System.out.println(source);
            e.printStackTrace();
        }
        return map;
    }

    private static String readFile(String fileName, List<String> ignoreTags) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader( fileName + ".xml"));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                if (checkIgnore(line, ignoreTags)) {
                    sb.append(line);
                    sb.append("\n");
                }
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

    private static boolean checkIgnore(String line, List<String> ignoreTags) {
        for (String s : ignoreTags) {
            String patternString = s;
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(line);
            boolean matches = matcher.matches();
            if (matches)
                return false;
        }
        return true;
    }
}
