package ai.tautona.lloyds.mailboxprocessor.service;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.Document;
import java.nio.file.Files;
import com.amazonaws.services.textract.model.*;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;


@Service
@Transactional
public class AmazonOCRService {


    public static void getKVMap(String localFile) throws IOException {

        File file = new File(localFile);
        byte[] fileContent = Files.readAllBytes(file.toPath());
        AmazonTextract client = AmazonTextractClientBuilder.defaultClient();

        AnalyzeDocumentRequest request = new AnalyzeDocumentRequest()
            .withDocument(new Document()
                .withBytes(ByteBuffer.wrap(fileContent))).withFeatureTypes(FeatureType.FORMS);


        AnalyzeDocumentResult result = client.analyzeDocument(request);


        //Get the text blocks
        List<Block> blocks = result.getBlocks();

        //get key and value maps
        List<Block> keyMap = new ArrayList<>();
        List<Block> valueMap = new ArrayList<>();
        List<Block> blockMap = new ArrayList<>();

        for (Block block : blocks) {
            blockMap.add(block);
            if (block.getBlockType().equals("KEY_VALUE_SET")) {
                if (block.getEntityTypes().contains("KEY")) {
                    keyMap.add(block);
                } else {
                    valueMap.add(block);
                }

            }

        }

        //Get Key Value relationship
        getKVMapRelationship(keyMap, valueMap, blockMap).forEach((k, v) -> System.out.println("key: " + k + " value:" + v));



    }

    public static HashMap<String, String> getKVMapRelationship(List<Block> key_map, List<Block> valueMap, List<Block> blockMap) throws IOException {
        HashMap<String, String> kvs = new HashMap<>();
        ;
        Block value_block;
        String key, val = "";
        for (Block key_block : key_map) {
            value_block = Find_value_block(key_block, valueMap);
            key = Get_text(key_block, blockMap);
            val = Get_text(value_block, blockMap);

            kvs.put(key, val);
        }

        return kvs;

    }

    public static Block Find_value_block(Block block, List<Block> valueMap) {
        Block valueBlock = new Block();
        for (Relationship relationship : block.getRelationships()) {
            if (relationship.getType().equals("VALUE")) {
                for (String value_id : relationship.getIds()) {

                    for (Block value : valueMap) {
                        if (value.getId().equals(value_id)) {
                            valueBlock = value;
                        }

                    }

                }

            }

        }
        return valueBlock;

    }


    public static String Get_text(Block result, List<Block> blockMap) {
        String text = "";
        Block word2= new Block();
        try {

            if (result != null
                && CollectionUtils.isNotEmpty(result.getRelationships())) {

                for (Relationship relationship : result.getRelationships()) {

                    if (relationship.getType().equals("CHILD")) {

                        for (String id : relationship.getIds()) {

                            Block word= (blockMap.stream().filter(x-> x.getId().equals(id)).findFirst().orElse(word2));


                            if (word.getBlockType().equals("WORD")) {
                                text += word.getText() + " ";
                            } else if (word.getBlockType().equals("SELECTION_ELEMENT")) {

                                if (word.getSelectionStatus().equals("SELECTED")) {
                                    text += "X ";
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println(e);
        }
        return text;
    }

    public static void main (String[]args) throws IOException {

        String fileStr = "/home/daniel/Documents/atrium_sources/accordImage-1.png";

        AmazonOCRService.getKVMap(fileStr);

        System.out.println("Done!");
    }
}
