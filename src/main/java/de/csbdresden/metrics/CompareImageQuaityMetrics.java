package de.csbdresden.metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.metrics.imagequality.PSNR;
import net.imglib2.algorithm.metrics.imagequality.SSIM;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.DoubleColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;

public class CompareImageQuaityMetrics
{
	public final static String folder = "/Users/deschamp/git/compare-metrics/jupyter/image-quality/data/";

	public static void main( String... args ) throws IOException
	{
		ImageJ ij = new ImageJ();

		// get images
		RandomAccessibleInterval dgt = ij.scifio().datasetIO().open( folder + "gt.tiff" );
		RandomAccessibleInterval dpred = ij.scifio().datasetIO().open( folder + "pred.tiff" );

		RandomAccessibleInterval< UnsignedByteType > gt = convertToUnsignedByte( dgt );
		RandomAccessibleInterval< UnsignedByteType > pred = convertToUnsignedByte( dpred );

		//RandomAccessibleInterval<IntType> gt_1 = Views.hyperSlice(gt, 2, 0);
		//ImageJFunctions.show(gt_1);

		runIndividualImages( ij, gt, pred );
	}

	private static void runIndividualImages( ImageJ ij, RandomAccessibleInterval< UnsignedByteType > gt, RandomAccessibleInterval< UnsignedByteType > pred )
	{
		ArrayList< Double > psnrResults = new ArrayList<>();
		ArrayList< Double > ssimResults = new ArrayList<>();

		for ( int i = 0; i < gt.dimension( 2 ); i++ )
		{
			RandomAccessibleInterval< UnsignedByteType > gtS = Views.hyperSlice( gt, 2, i );
			RandomAccessibleInterval< UnsignedByteType > predS = Views.hyperSlice( pred, 2, i );

			psnrResults.add( PSNR.computeMetrics(gtS, predS) );
			ssimResults.add( SSIM.computeMetrics(gtS, predS, SSIM.Filter.GAUSS, 1.5) );
		}

		// load python results
		ArrayList< Double > psnrPython = readCSV( folder + "psnr.txt" );
		ArrayList< Double > ssimPython = readCSV( folder + "ssim.txt" );

		// show result
		showTable( ij.ui(), "PSNR", psnrResults, psnrPython );
		showTable( ij.ui(), "SSIM", ssimResults, ssimPython );
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

	private static < T extends RealType< T > > RandomAccessibleInterval< UnsignedByteType > convertToUnsignedByte( RandomAccessibleInterval< T > img )
	{
		return Converters.convert( img, new ByteConverter< T >(), new UnsignedByteType() );
	}


	public static class ByteConverter< R extends RealType< R > > implements
			Converter< R, UnsignedByteType >
	{

		@Override
		public void convert( final R input, final UnsignedByteType output )
		{
			output.set( ( byte ) input.getRealFloat() );
		}
	}
}