package de.csbdresden.metrics;

import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.metrics.segmentation.MultiMetrics;
import net.imglib2.algorithm.metrics.segmentation.SEG;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.DoubleColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class CompareCommand {

    public static void main(String... args) throws IOException {
        ImageJ ij = new ImageJ();

        // get images
        RandomAccessibleInterval dgt = ij.scifio().datasetIO().open("/Users/deschamp/git/compare-metrics/src/main/resources/gt.tiff");
        RandomAccessibleInterval dpred = ij.scifio().datasetIO().open("/Users/deschamp/git/compare-metrics/src/main/resources/pred.tiff");

        RandomAccessibleInterval<IntType> gt = convertToInt(dgt);
        RandomAccessibleInterval<IntType> pred = convertToInt(dpred);

        //ImageJFunctions.show(gt);

        ///////////////
        // SEG
        ArrayList<Double> segResults = new ArrayList<>();
        SEG segMetrics = new SEG();
        for(int i=0; i<gt.dimension(0);i++){
            RandomAccessibleInterval<IntType> gtS = Views.hyperSlice(gt, 0, i);
            RandomAccessibleInterval<IntType> predS = Views.hyperSlice(pred, 0, i);
            
            double d = segMetrics.computeMetrics(gtS, predS);
            segResults.add(d);
        }

        // load seg python
        ArrayList<Double> segPython = readCSV("/Users/deschamp/git/compare-metrics/src/main/resources/seg.txt");

        // show result
        showTable(ij.ui(), "SEG", segResults, segPython);

        ///////////////
        // MultiMetrics
        ArrayList<Double> accResults = new ArrayList<>();
        MultiMetrics multMetrics = new MultiMetrics(MultiMetrics.Metrics.ACCURACY, 0.5);
        for(int i=0; i<gt.dimension(0);i++){
            RandomAccessibleInterval<IntType> gtS = Views.hyperSlice(gt, 0, i);
            RandomAccessibleInterval<IntType> predS = Views.hyperSlice(pred, 0, i);

            double d = multMetrics.computeMetrics(gtS, predS);
            accResults.add(d);
        }

        // load seg python
        ArrayList<Double> accPython = readCSV("/Users/deschamp/git/compare-metrics/src/main/resources/accuracy.txt");

        // show result
        showTable(ij.ui(), "Accuracy", accResults, accPython);
    }

    private static void showTable(UIService uiService, String title, ArrayList<Double> java, ArrayList<Double> py){
        Table table = new DefaultGenericTable();
        DoubleColumn cJ = new DoubleColumn();
        DoubleColumn cP = new DoubleColumn();

        for(int i=0; i<java.size(); i++){
            cJ.add(java.get(i));
            cP.add(py.get(i));
        }

        table.add(cJ);
        table.add(cP);

        table.setColumnHeader(0, "Java");
        table.setColumnHeader(1, "Python");

        uiService.show(title, table);
    }

    private static ArrayList<Double> readCSV(String path){
        ArrayList<Double> l = new ArrayList<>();

        // create a reader
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path))) {

            // CSV file delimiter
            String DELIMITER = ",";

            // read the file line by line
            String line;
            while ((line = br.readLine()) != null) {

                // convert line into columns
                String[] columns = line.split(DELIMITER);

                for (String s: columns){
                    if(s.equals(" nan")){
                        l.add(Double.NaN);
                    } else {
                        l.add(Double.valueOf(s));
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return l;
    }

    private static <T extends RealType<T>> RandomAccessibleInterval<IntType> convertToInt(RandomAccessibleInterval<T> img) {
        return Converters.convert(img, new RealIntConverter<T>(), new IntType());
    }

    public static class RealIntConverter<R extends RealType<R>> implements
            Converter<R, IntType> {

        @Override
        public void convert(final R input, final IntType output) {
            output.set((int) input.getRealFloat());
        }
    }

}