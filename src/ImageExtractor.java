import java.io.IOException;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;

public class ImageExtractor extends PDFStreamEngine {

    public static void main(String[] args) throws IOException {
        for (String path: args) {
            Path p = Paths.get(path);
            if (Files.isDirectory(p)) {
                FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toString().endsWith(".pdf")) {
                            processFile(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                };
                Files.walkFileTree(p, visitor);
            } else {
                processFile(p);
            }
        }
    }

    static void processFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        String outPath = path.toString().replace(".pdf", ".image");
        ImageExtractor ie = new ImageExtractor();
        ie.output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"));
        ie.process(doc);
        ie.output.close();
    }

    static void countFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        ImageExtractor ie = new ImageExtractor();
        ie.process(doc);
        ie.output.close();
    }

    int pageNo;
    Writer output;

    public ImageExtractor() throws IOException {
        addOperator(new Concatenate());
        addOperator(new DrawObject());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new SetMatrix());
    }

    public void process(PDDocument doc) throws IOException {
        pageNo = 1;
        for (PDPage page : doc.getPages()) {
            processPage(page);
            pageNo++;
        }
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if("Do".equals(operation)) {
            COSName objectName = (COSName)operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);

            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject)xobject;
                output.write(String.valueOf(pageNo)); output.write("\t");

                // position (x, y, width, height)
                Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                output.write(ctmNew.getTranslateX() + "\t" + ctmNew.getTranslateY() + "\t");
                output.write(ctmNew.getScalingFactorX() + "\t" + ctmNew.getScalingFactorY());
                output.write("\n");
            }
            else if(xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject)xobject;
                showForm(form);
            }
        }
        else {
            super.processOperator(operator, operands);
        }
    }
}