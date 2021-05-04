package de.csbdresden.metrics.det;

import java.util.Arrays;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.metrics.segmentation.ConfusionMatrix;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;


// TODO methods javadoc is wrong
// TODO talk about minimality condition
public class DETMetrics
{
	private final static int T_AXIS = 3;

	protected static class Weights
	{
		final protected double wFP;

		final protected double wFN;

		final protected double wNS;

		public Weights()
		{
			this.wNS = 5.;
			this.wFN = 10.;
			this.wFP = 1.;
		}

		public Weights( double wNS, double wFN, double wFP )
		{
			this.wNS = wNS;
			this.wFN = wFN;
			this.wFP = wFP;
		}
	}

	protected static class Results
	{
		final protected int nGT;

		final protected int maxNS;

		final protected double aogm;

		public Results( int nGT, int maxNS, double aogm )
		{
			this.nGT = nGT;
			this.maxNS = maxNS;
			this.aogm = aogm;
		}
	}

	/**
	 * Compute a global metrics score between labels from a ground-truth and a predicted image. The
	 * method expects images of dimension XYZT. The score is computed over the XYZ volume (or XY if
	 * Z is of depth 1) and averaged over all ground-truth labels. If both images are empty (only
	 * pixels with value 0), then the metrics score is NaN.
	 *
	 * @param groundTruth
	 * 		Ground-truth image
	 * @param prediction
	 * 		Predicted image
	 * @param <I>
	 * 		Ground-truth pixel type
	 * @param <J>
	 * 		Prediction pixel type
	 *
	 * @return Metrics score
	 */
	public static < I extends IntegerType< I >, J extends IntegerType< J > > double computeMetrics(
			RandomAccessibleInterval< I > groundTruth,
			RandomAccessibleInterval< J > prediction )
	{
		if ( !Arrays.equals( groundTruth.dimensionsAsLongArray(), prediction.dimensionsAsLongArray() ) )
			throw new IllegalArgumentException( "Image dimensions must match." );

		// check if it is a time-lapse
		boolean timeLapse = false;
		if ( groundTruth.dimensionsAsLongArray().length > T_AXIS )
		{
			timeLapse = groundTruth.dimension( T_AXIS ) > 1;
		}

		// default weights
		Weights w = new Weights();

		if ( timeLapse )
		{
			return runAverageOverTime( groundTruth, prediction );
		}
		else
		{
			final Results r = runSingle( groundTruth, prediction, w );
			return computeDET( w, r.nGT, r.aogm );
		}
	}

	private static < I extends IntegerType< I >, J extends IntegerType< J > > double runAverageOverTime(
			RandomAccessibleInterval< I > groundTruth,
			RandomAccessibleInterval< J > prediction )
	{
		int nFrames = Intervals.dimensionsAsIntArray( groundTruth )[ T_AXIS ];

		// default weights
		Weights w = new Weights();
		double aogm = 0.;
		int nGT = 0;

		// run over all time indices, and compute metrics on each XY or XYZ hyperslice
		for ( int i = 0; i < nFrames; i++ )
		{
			final RandomAccessibleInterval< I > gtFrame = Views.hyperSlice( groundTruth, T_AXIS, i );
			final RandomAccessibleInterval< J > predFrame = Views.hyperSlice( prediction, T_AXIS, i );

			final Results r = runSingle( gtFrame, predFrame, w );

			nGT += r.nGT;
			aogm += r.aogm;

			// todo how to remember maxNS for minimality conditions check?
		}

		return computeDET( w, nGT, aogm );
	}

	protected static < I extends IntegerType< I >, J extends IntegerType< J > > Results runSingle(
			RandomAccessibleInterval< I > groundTruth,
			RandomAccessibleInterval< J > prediction,
			Weights w )
	{
		// compute confusion matrix
		final ConfusionMatrix confusionMatrix = new ConfusionMatrix( groundTruth, prediction );
		int n = confusionMatrix.getNumberGroundTruthLabels();

		// compute cost matrix
		final double[][] costMatrix = computeCostMatrix( confusionMatrix );

		return computeFinalScore( confusionMatrix, costMatrix, w );
	}

	private static Results computeFinalScore( ConfusionMatrix cm, double[][] costMatrix, Weights w )
	{
		if ( costMatrix.length != 0 && costMatrix[ 0 ].length != 0 )
		{
			final int M = costMatrix.length;
			final int N = costMatrix[ 0 ].length;

			// acyclic oriented graphs matching measure
			double aogm = 0.;

			// add false negative contributions
			for ( int i = 0; i < M; i++ )
			{
				boolean matched = false;
				for ( int j = 0; j < N; j++ )
				{
					if ( costMatrix[ i ][ j ] > 0 )
					{
						matched = true;
						break;
					}
				}

				if ( !matched )
					aogm += w.wFN;
			}

			// add false positive and split event contribution
			int maxNS = 1;
			int numMatched = 0;
			for ( int j = 0; j < N; j++ )
			{
				int n = 0;
				for ( int i = 0; i < M; i++ )
				{
					if ( costMatrix[ i ][ j ] > 0 )
						n++;
				}

				if ( n == 0 ) // false positive
				{
					aogm += w.wFP;
				}
				else if ( n > 1 ) // missed splits
				{
					if ( n > maxNS )
						maxNS = n;

					aogm += ( n - 1 ) * w.wNS;
				}
			}

			return new Results( M, maxNS, aogm );
		}

		return new Results( costMatrix.length, 0, 0. );
	}

	protected static double computeDET( Weights w, int nGT, double aogm )
	{
		double aogm0 = w.wFN * nGT;

		double aogm_d = aogm;
		if ( aogm_d > aogm0 )
			aogm_d = aogm0;

		return aogm0 > 0 ? 1 - aogm_d / aogm0 : Double.NaN;
	}

	/**
	 * Check whether the minimality condition is fulfilled.
	 *
	 * @param w Weights used to get the results
	 * @param r Results
	 * @return True if the condition is met, false otherwise.
	 * @see <a href="https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0144959">Equation 13 in the original publication</a>
	 */
	// todo but need to maintain maxNS, which is not the case here
	public static boolean checkMinimalityCondition( Weights w, Results r )
	{
		return ( r.maxNS - 1 ) * w.wNS <= w.wFP + r.maxNS * w.wFN;
	}

	// todo
	///////////////// to remove if ever to move to imglib2-agorithms


	protected static double[][] computeCostMatrix( ConfusionMatrix cM )
	{
		int M = cM.getNumberGroundTruthLabels();
		int N = cM.getNumberPredictionLabels();

		// empty cost matrix
		// make sure to obtain a rectangular matrix, with Npred > Ngt, in order
		// to avoid empty assignments if using Munkres-Kuhn
		double[][] costMatrix = new double[ M ][ N ];

		// fill in cost matrix
		for ( int i = 0; i < M; i++ )
		{
			for ( int j = 0; j < N; j++ )
			{
				costMatrix[ i ][ j ] = getLocalIoUScore( cM, i, j );
			}
		}

		return costMatrix;
	}


	protected static double getLocalIoUScore( ConfusionMatrix cM, int i, int j )
	{
		double intersection = cM.getIntersection( i, j );
		double gtSize = cM.getGroundTruthLabelSize( i );

		double overlap = intersection / gtSize;
		if ( overlap > 0.5 )
		{
			double predSize = cM.getPredictionLabelSize( j );

			return intersection / ( gtSize + predSize - intersection );
		}

		return 0.;
	}
}
