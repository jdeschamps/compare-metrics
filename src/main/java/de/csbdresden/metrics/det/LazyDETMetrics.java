package de.csbdresden.metrics.det;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;

import static de.csbdresden.metrics.det.DETMetrics.computeDET;

public class LazyDETMetrics
{

	private AtomicInteger nGT = new AtomicInteger( 0 );

	private AtomicInteger maxNS = new AtomicInteger( 0 );

	private AtomicLong sumAogm = new AtomicLong( 0 );

	final private DETMetrics.Weights w;

	public LazyDETMetrics(double wNS, double wFP, double wFN){
		w = new DETMetrics.Weights(wNS, wFP, wFN);
	}


	/**
	 * Add a new images pair and compute its contribution to the SEG metrics. The current SEG score
	 * can be computed by calling {@link #computeScore()}.
	 *
	 * @param groundTruth
	 * 		Ground-truth image
	 * @param prediction
	 * 		Predicted image
	 * @param <I>
	 * 		Ground-truth pixel type
	 * @param <J>
	 * 		Prediction pixel type
	 */
	public < I extends IntegerType< I >, J extends IntegerType< J > > void addTimePoint(
			RandomAccessibleInterval< I > groundTruth,
			RandomAccessibleInterval< J > prediction )
	{

		if ( !Arrays.equals( groundTruth.dimensionsAsLongArray(), prediction.dimensionsAsLongArray() ) )
			throw new IllegalArgumentException( "Image dimensions must match." );

		// compute SEG between the two images
		final DETMetrics.Results result = DETMetrics.runSingle( groundTruth, prediction, w );


		addResult(result);
	}

	private void addResult( final DETMetrics.Results result )
	{
		nGT.addAndGet( result.nGT );
		if(maxNS.get() < result.maxNS)
			maxNS.set( result.maxNS );

		addToAtomicLong( sumAogm, result.aogm );
	}

	/**
	 * Compute the total SEG score. If no image was added, or all images were empty, then the SEG score
	 * is NaN.
	 *
	 * @return SEG score
	 */
	public double computeScore()
	{
		return computeDET(w, nGT.get(), sumAogm.get());
	}

	private void addToAtomicLong( AtomicLong a, double b )
	{
		a.set( Double.doubleToRawLongBits( Double.longBitsToDouble( a.get() ) + b ) );
	}

	private double atomicLongToDouble( AtomicLong a )
	{
		return Double.longBitsToDouble( a.get() );
	}
}