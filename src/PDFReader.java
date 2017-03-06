import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdfparser.PDFStreamParser;

public class PDFReader {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: pdfparser <file|directory>");
        }
        else {
            PDDocument doc = null;
            try {
                File file = new File(args[0]);
                ArrayList<File> files = new ArrayList<>();
                if (file.isFile()) files.add(file);
                else if(file.isDirectory()) {
                    for (File f : file.listFiles()) {
                        if (f.isFile() && f.getPath().endsWith(".pdf")) files.add(f);
                    }
                }
                for (File f : files) {
                    doc = PDDocument.load(f);
                    (new DrawExtractor(doc.getPage(0))).run();
                    //PDFReader.process(doc);
                    //(new TextExtractor()).process(doc, f.getName()+".txt");
                    //(new ImageExtractor()).process(doc);
                }
            }
            finally {
                if (doc != null) doc.close();
            }
        }
    }

    public static void process(PDDocument doc) throws IOException {
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        for (int i = 0; i < tokens.size(); i++) {
            Object t = tokens.get(i);
            System.out.println(t);
        }
    }
}
