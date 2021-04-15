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
import java.util.HashMap;

public class CompareMetrics {

    public static String folder = "/Users/deschamp/git/compare-metrics/jupyter/data/";

    public static void main(String... args) throws IOException {
        ImageJ ij = new ImageJ();

        // get images
        RandomAccessibleInterval dgt = ij.scifio().datasetIO().open(folder+"gt.tiff");
        RandomAccessibleInterval dpred = ij.scifio().datasetIO().open(folder+"pred.tiff");

        RandomAccessibleInterval<IntType> gt = convertToInt(dgt);
        RandomAccessibleInterval<IntType> pred = convertToInt(dpred);

        //RandomAccessibleInterval<IntType> gt_1 = Views.hyperSlice(gt, 2, 0);
        //ImageJFunctions.show(gt_1);

        runFullComparison(ij, gt, pred);
    }

    private static void runFullComparison(ImageJ ij, RandomAccessibleInterval<IntType> gt, RandomAccessibleInterval<IntType> pred){
        ///////////////
        // SEG
        ArrayList<Double> segResults = new ArrayList<>();
        SEG segMetrics = new SEG();

        HashMap< MultiMetrics.Metrics, ArrayList<Double> > multiResults = new HashMap<>();
        MultiMetrics.Metrics.stream().forEach(m -> multiResults.put(m, new ArrayList<>()));

        MultiMetrics multMetrics = new MultiMetrics(0.5);
        for(int i=0; i<gt.dimension(0);i++){
            RandomAccessibleInterval<IntType> gtS = Views.hyperSlice(gt, 2, i);
            RandomAccessibleInterval<IntType> predS = Views.hyperSlice(pred, 2, i);

            double d = segMetrics.computeMetrics(gtS, predS);
            segResults.add(d);

            multMetrics.computeMetrics(gtS, predS);
            MultiMetrics.Metrics.stream().forEach(m -> multiResults.get(m).add(multMetrics.getScore(m)));
        }

        // load python results
        ArrayList<Double> segPython = readCSV(folder+"seg.txt");

        HashMap< MultiMetrics.Metrics, ArrayList<Double> > multiPython = new HashMap<>();
        multiPython.put(MultiMetrics.Metrics.ACCURACY, readCSV(folder+"accuracy.txt"));
        multiPython.put(MultiMetrics.Metrics.MEAN_TRUE_IOU, readCSV(folder+"mean_true_score.txt"));
        multiPython.put(MultiMetrics.Metrics.MEAN_MATCHED_IOU, readCSV(folder+"mean_matched_score.txt"));
        multiPython.put(MultiMetrics.Metrics.F1, readCSV(folder+"f1.txt"));
        multiPython.put(MultiMetrics.Metrics.TP, readCSV(folder+"tp.txt"));
        multiPython.put(MultiMetrics.Metrics.FP, readCSV(folder+"fp.txt"));
        multiPython.put(MultiMetrics.Metrics.FN, readCSV(folder+"fn.txt"));
        multiPython.put(MultiMetrics.Metrics.PRECISION, readCSV(folder+"precision.txt"));
        multiPython.put(MultiMetrics.Metrics.RECALL, readCSV(folder+"recall.txt"));

        // show result
        showTable(ij.ui(), "SEG", segResults, segPython);
        MultiMetrics.Metrics.stream().forEach(m -> showTable(ij.ui(), m.getName(), multiResults.get(m), multiPython.get(m)));

    }

    private static void showTable(UIService uiService, String title, ArrayList<Double> java, ArrayList<Double> py){
        Table table = new DefaultGenericTable();
        DoubleColumn cJ = new DoubleColumn();
        DoubleColumn cP = new DoubleColumn();

        System.out.println("------------------------------");
        System.out.println("------------------------------");
        System.out.println("--------- "+title+" ----------");
        for(int i=0; i<java.size(); i++){
            cJ.add(java.get(i));
            cP.add(py.get(i));

            if(Math.abs(java.get(i)-py.get(i)) > 0.0001){
                System.out.println(i+" - "+java.get(i)+" - "+py.get(i));
            }
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