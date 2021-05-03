package de.csbdresden.metrics;

import de.csbdresden.metrics.CTC.SEGCTC;
import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.metrics.segmentation.LazyMultiMetrics;
import net.imglib2.algorithm.metrics.segmentation.LazySEGMetrics;
import net.imglib2.algorithm.metrics.segmentation.MultiMetrics;
import net.imglib2.algorithm.metrics.segmentation.SEGMetrics;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
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

/**
 * So this class is basically a bit of a mess. The aim is to compare the metrics
 * implemented for imglib2-algrithms with those obtained using python code from
 * reference repositories (StarDist for the MultiMetrics and denoiseg for the
 * SEG).
 *
 * The python code can be found in the jupyter notebook folder. One notebook is
 * used to create a stack of random images with labelings. The other notebooks
 * run the python metrics and save the result.
 *
 * This class loads the same data, and the result from the python code. It runs
 * three different comparisons:
 * - Metrics result on individual images
 * - Metrics result when feeding in the metrics a stack of images (slices along time)
 * - Metrics result when giving the frames one by one to a Lazy metrics
 *
 * The individual results are shown as a table. Outputs to the console are only
 * the frames or averages that are different between java and python. If it does
 * not print to the console, then the two are the same.
 */
public class CompareSegmentationMetrics
{

	public final static String folder = "/Users/deschamp/git/compare-metrics/jupyter/segmentation/data/";

	public static void main( String... args ) throws IOException
	{
		ImageJ ij = new ImageJ();

		// get images
		RandomAccessibleInterval dgt = ij.scifio().datasetIO().open( folder + "gt.tiff" );
		RandomAccessibleInterval dpred = ij.scifio().datasetIO().open( folder + "pred.tiff" );

		RandomAccessibleInterval< IntType > gt = convertToInt( dgt );
		RandomAccessibleInterval< IntType > pred = convertToInt( dpred );

		//RandomAccessibleInterval<IntType> gt_1 = Views.hyperSlice(gt, 2, 0);
		//ImageJFunctions.show(gt_1);

		//runIndividualImages( ij, gt, pred );
		//runAverage( ij, gt, pred );
		//runLazy( ij, gt, pred );
		runSegCTC(ij, gt, pred);
		//runSegCTCIndividuals(ij, gt, pred);
	}

	private static void runAverage( final ImageJ ij, final RandomAccessibleInterval< IntType > groundtruth, final RandomAccessibleInterval< IntType > prediction )
	{
		final RandomAccessibleInterval< IntType > gt = convertToTime( groundtruth );
		final RandomAccessibleInterval< IntType > pred = convertToTime( prediction );

		// get metrics
		SEGMetrics segMetrics = new SEGMetrics();
		MultiMetrics multMetrics = new MultiMetrics();

		double segJava = segMetrics.computeMetrics( gt, pred );
		final HashMap< MultiMetrics.Metrics, Double > resJava = multMetrics.computeMetrics( gt, pred, 0.5 );

		// load python results
		double segPython = readCSV( folder + "seg_av.txt" ).get( 0 );

		HashMap< MultiMetrics.Metrics, Double > multiPython = new HashMap<>();
		multiPython.put( MultiMetrics.Metrics.ACCURACY, readCSV( folder + "accuracy_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.MEAN_TRUE_IOU, readCSV( folder + "mean_true_score_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.MEAN_MATCHED_IOU, readCSV( folder + "mean_matched_score_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.F1, readCSV( folder + "f1_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.TP, readCSV( folder + "tp_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.FP, readCSV( folder + "fp_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.FN, readCSV( folder + "fn_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.PRECISION, readCSV( folder + "precision_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.RECALL, readCSV( folder + "recall_av.txt" ).get( 0 ) );

		System.out.println( "--------------------------------------" );
		System.out.println( "-------------- Averages --------------" );

		System.out.println( "--------- SEG" );
		printIfDiff( segJava, segPython );

		System.out.println( "--------- Multi" );
		printIfDiff( resJava, multiPython );
	}

	private static void runSegCTC( final ImageJ ij, final RandomAccessibleInterval< IntType > groundtruth, final RandomAccessibleInterval< IntType > prediction )
	{
		final RandomAccessibleInterval< UnsignedShortType > gt = Converters.convert( convertToTime( groundtruth ), ( i, o ) -> o.set( i.getInt() ), new UnsignedShortType() );
		final RandomAccessibleInterval< UnsignedShortType > pred = Converters.convert( convertToTime( prediction ), ( i, o ) -> o.set( i.getInt() ), new UnsignedShortType() );

		// quick and dirty: the SEG from imglib2 expects a T dimension, otherwise it runs 3D labels

		double segImgLib = SEGMetrics.computeMetrics( gt, pred );
		double segCTC = new SEGCTC().calculate( gt, pred );

		System.out.println("SEG average: "+segCTC+" vs "+segImgLib);
	}

	private static void runSegCTCIndividuals( final ImageJ ij, final RandomAccessibleInterval< IntType > groundtruth, final RandomAccessibleInterval< IntType > prediction )
	{
		final RandomAccessibleInterval< UnsignedShortType > gt = Converters.convert( groundtruth, ( i, o ) -> o.set( i.getInt() ), new UnsignedShortType() );
		final RandomAccessibleInterval< UnsignedShortType > pred = Converters.convert( prediction, ( i, o ) -> o.set( i.getInt() ), new UnsignedShortType() );

		System.out.println( "--------------------------------------" );
		System.out.println( "-------------- Individuals --------------" );

		ArrayList< Double > segResults = new ArrayList<>();
		ArrayList< Double > segCTCResults = new ArrayList<>();
		for ( int i = 0; i < gt.dimension( 2 ); i++ )
		{
			RandomAccessibleInterval< UnsignedShortType > gtS = Views.hyperSlice( gt, 2, i );
			RandomAccessibleInterval< UnsignedShortType > predS = Views.hyperSlice( pred, 2, i );

			double ctc = new SEGCTC().calculate(gtS, predS);
			double imglib = SEGMetrics.computeMetrics(gtS, predS);

			segResults.add( imglib );
			segCTCResults.add( ctc );
		}

		showTable( ij.ui(), "SEG - singles", segCTCResults, segResults );
	}

	private static void runLazy( final ImageJ ij, final RandomAccessibleInterval< IntType > gt, final RandomAccessibleInterval< IntType > pred )
	{
		// get lazy metrics
		LazySEGMetrics lazySEG = new LazySEGMetrics();
		LazyMultiMetrics lazyMetrics = new LazyMultiMetrics( 0.5 );
		for ( int i = 0; i < gt.dimension( 2 ); i++ )
		{
			RandomAccessibleInterval< IntType > gtS = Views.hyperSlice( gt, 2, i );
			RandomAccessibleInterval< IntType > predS = Views.hyperSlice( pred, 2, i );

			lazySEG.addTimePoint( gtS, predS );
			lazyMetrics.addTimePoint( gtS, predS );
		}
		double lazySegJava = lazySEG.computeScore();
		final HashMap< MultiMetrics.Metrics, Double > lazyResJava = lazyMetrics.computeScore();

		// load python results
		double segPython = readCSV( folder + "seg_av.txt" ).get( 0 );

		HashMap< MultiMetrics.Metrics, Double > multiPython = new HashMap<>();
		multiPython.put( MultiMetrics.Metrics.ACCURACY, readCSV( folder + "accuracy_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.MEAN_TRUE_IOU, readCSV( folder + "mean_true_score_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.MEAN_MATCHED_IOU, readCSV( folder + "mean_matched_score_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.F1, readCSV( folder + "f1_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.TP, readCSV( folder + "tp_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.FP, readCSV( folder + "fp_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.FN, readCSV( folder + "fn_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.PRECISION, readCSV( folder + "precision_av.txt" ).get( 0 ) );
		multiPython.put( MultiMetrics.Metrics.RECALL, readCSV( folder + "recall_av.txt" ).get( 0 ) );

		System.out.println( "--------------------------------------" );
		System.out.println( "-------------- Averages - Lazy--------------" );

		System.out.println( "--------- SEG" );
		printIfDiff( lazySegJava, segPython );

		System.out.println( "--------- Multi" );
		printIfDiff( lazyResJava, multiPython );
	}

	private static RandomAccessibleInterval< IntType > convertToTime(final RandomAccessibleInterval< IntType > im)
	{
		// there is probably a much better way to do that but I couldn't figure it out
		ArrayList< RandomAccessibleInterval< IntType > > planes = new ArrayList<>();
		for ( int i = 0; i < im.dimension( 2 ); i++ )
		{
			RandomAccessibleInterval< IntType > slice = Views.addDimension(Views.addDimension( Views.hyperSlice( im, 2, i ), 0, 0 ),0,0);

			planes.add( slice );
		}

		return Views.concatenate( 3, planes );
	}

	private static void printIfDiff( double a, double b )
	{
		if ( Math.abs( a - b ) > 0.000001 || Double.compare( Double.NaN, a + b ) == 0 )
			System.out.println( a + " - " + b );
	}

	private static void printIfDiff( HashMap< MultiMetrics.Metrics, Double > a, HashMap< MultiMetrics.Metrics, Double > b )
	{
		for ( MultiMetrics.Metrics m : a.keySet() )
		{
			System.out.println( "----- " + m.getName() );
			if ( Math.abs( a.get( m ) - b.get( m ) ) > 0.000001 || Double.compare( Double.NaN, a.get( m ) + b.get( m ) ) == 0 )
				System.out.println( a.get( m ) + " - " + b.get( m ) );
		}
	}

	private static void runIndividualImages( ImageJ ij, RandomAccessibleInterval< IntType > gt, RandomAccessibleInterval< IntType > pred )
	{

		System.out.println( "--------------------------------------" );
		System.out.println( "-------------- Singles --------------" );

		ArrayList< Double > segResults = new ArrayList<>();
		SEGMetrics segMetrics = new SEGMetrics();

		HashMap< MultiMetrics.Metrics, ArrayList< Double > > multiResults = new HashMap<>();
		MultiMetrics.Metrics.stream().forEach( m -> multiResults.put( m, new ArrayList<>() ) );

		MultiMetrics multMetrics = new MultiMetrics();
		for ( int i = 0; i < gt.dimension( 2 ); i++ )
		{
			RandomAccessibleInterval< IntType > gtS = Views.hyperSlice( gt, 2, i );
			RandomAccessibleInterval< IntType > predS = Views.hyperSlice( pred, 2, i );

			double d = segMetrics.computeMetrics( gtS, predS );
			segResults.add( d );

			final HashMap< MultiMetrics.Metrics, Double > res = multMetrics.computeMetrics( gtS, predS, 0.5 );
			MultiMetrics.Metrics.stream().forEach( m -> multiResults.get( m ).add( res.get( m ) ) );
		}

		// load python results
		ArrayList< Double > segPython = readCSV( folder + "seg.txt" );

		HashMap< MultiMetrics.Metrics, ArrayList< Double > > multiPython = new HashMap<>();
		multiPython.put( MultiMetrics.Metrics.ACCURACY, readCSV( folder + "accuracy.txt" ) );
		multiPython.put( MultiMetrics.Metrics.MEAN_TRUE_IOU, readCSV( folder + "mean_true_score.txt" ) );
		multiPython.put( MultiMetrics.Metrics.MEAN_MATCHED_IOU, readCSV( folder + "mean_matched_score.txt" ) );
		multiPython.put( MultiMetrics.Metrics.F1, readCSV( folder + "f1.txt" ) );
		multiPython.put( MultiMetrics.Metrics.TP, readCSV( folder + "tp.txt" ) );
		multiPython.put( MultiMetrics.Metrics.FP, readCSV( folder + "fp.txt" ) );
		multiPython.put( MultiMetrics.Metrics.FN, readCSV( folder + "fn.txt" ) );
		multiPython.put( MultiMetrics.Metrics.PRECISION, readCSV( folder + "precision.txt" ) );
		multiPython.put( MultiMetrics.Metrics.RECALL, readCSV( folder + "recall.txt" ) );

		// show result
		showTable( ij.ui(), "SEG - singles", segResults, segPython );
		MultiMetrics.Metrics.stream().forEach( m -> showTable( ij.ui(), m.getName() + " - singles", multiResults.get( m ), multiPython.get( m ) ) );

		//sum(multiResults.get( MultiMetrics.Metrics.TP ), multiPython.get( MultiMetrics.Metrics.TP ));
	}

	private static void sum(ArrayList< Double > java, ArrayList< Double > py){
		System.out.println("-------------- sum");
		System.out.println(java.stream().reduce( (a, b) -> a+b ));
		System.out.println(py.stream().reduce( (a, b) -> a+b ));
	}

	private static void showTable( UIService uiService, String title, ArrayList< Double > java, ArrayList< Double > py )
	{
		Table table = new DefaultGenericTable();
		DoubleColumn cJ = new DoubleColumn();
		DoubleColumn cP = new DoubleColumn();

		System.out.println( "--------- " + title + " ----------" );
		for ( int i = 0; i < java.size(); i++ )
		{
			cJ.add( java.get( i ) );
			cP.add( py.get( i ) );

			if ( Math.abs( java.get( i ) - py.get( i ) ) > 0.000001 || Double.compare( Double.NaN, java.get( i ) + py.get( i ) ) == 0 )
			{
				System.out.println( i + " - " + java.get( i ) + " - " + py.get( i ) );
			}
		}

		table.add( cJ );
		table.add( cP );

		table.setColumnHeader( 0, "Java" );
		table.setColumnHeader( 1, "Python" );

		uiService.show( title, table );
	}

	private static ArrayList< Double > readCSV( String path )
	{
		ArrayList< Double > l = new ArrayList<>();

		// create a reader
		try ( BufferedReader br = Files.newBufferedReader( Paths.get( path ) ) )
		{

			// CSV file delimiter
			String DELIMITER = ",";

			// read the file line by line
			String line;
			while ( ( line = br.readLine() ) != null )
			{

				// convert line into columns
				String[] columns = line.split( DELIMITER );

				for ( String s : columns )
				{
					if ( s.equals( " nan" ) )
					{
						l.add( Double.NaN );
					}
					else
					{
						l.add( Double.valueOf( s ) );
					}
				}
			}
		}
		catch ( IOException ex )
		{
			ex.printStackTrace();
		}

		return l;
	}

	private static < T extends RealType< T > > RandomAccessibleInterval< IntType > convertToInt( RandomAccessibleInterval< T > img )
	{
		return Converters.convert( img, new RealIntConverter< T >(), new IntType() );
	}

	public static class RealIntConverter< R extends RealType< R > > implements
			Converter< R, IntType >
	{

		@Override
		public void convert( final R input, final IntType output )
		{
			output.set( ( int ) input.getRealFloat() );
		}
	}

}