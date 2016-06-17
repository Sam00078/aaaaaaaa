package cn.justec.www.xml;

import android.os.Bundle;

import org.dom4j.jaxb.JAXBModifier;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import android.support.v7.app.AppCompatActivity;
import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dom();
    }

    public void dom(){
        long lasting = System.currentTimeMillis();
        try {
            File f = new File("data_10k.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(f);
            NodeList nl = doc.getElementsByTagName("value");
            for (int i = 0; i < nl.getLength();
                 i++) {
                System.out.print("车牌号码:" + doc.getElementsByTagName("no").item(i).getFirstChild().getNodeValue());
                System.out.println("车主地址:" + doc.getElementsByTagName("addr").item(i).getFirstChild().getNodeValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
