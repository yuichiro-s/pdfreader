import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;

public class PDFReader {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: pdfparser <file|directory>");
        }
        else {
            PDDocument doc = null;
            try {
                File file = new File(args[0]);
                ArrayList<File> files = new ArrayList();
                if (file.isFile()) files.add(file);
                else if(file.isDirectory()) {
                    for (File f : file.listFiles()) {
                        if (f.isFile() && f.getPath().endsWith(".pdf")) files.add(f);
                    }
                }
                for (File f : files) {
                    doc = PDDocument.load(f);
                    //(new DrawExtractor2()).process(doc);
                    //DrawExtractor engine = new DrawExtractor(doc.getPage(2));
                    //engine.run();
                    (new TextExtractor()).process(doc, f.getName()+".txt");
                    //(new ImageExtractor()).process(doc);
                }
            }
            finally {
                if (doc != null) doc.close();
            }
        }
    }
}
