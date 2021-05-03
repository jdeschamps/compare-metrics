package de.csbdresden.metrics.CTC;

/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */

import io.scif.img.ImgIOException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.scijava.log.LogService;

/**
 * Shamelessly borrowed from :
 * https://github.com/CellTrackingChallenge/CTC-FijiPlugins
 *
 * and modified for comparison purposes
 */
public class SEGCTC
{
	///shortcuts to some Fiji services
	//private final LogService log;

	///specifies how many digits are to be expected in the input filenames
	public int noOfDigits = 3;


	/**
	 * Calculation option: do report list of discrepancies between the reference
	 * and computed tracking result.
	 * This is helpful for algorithm developers as it shows what, where and when
	 * was incorrect in their results.
	 */
	public boolean doLogReports = false;

	/** This switches SEG to review all result labels, in contrast to reviewing
	 all GT labels. With this enabled, one can see false positives but don't
	 see false negatives. */
	public boolean doAllResReports = false;

	/** Set of time points that caller wishes to process despite the folder
	 with GT contains much more. If this attribute is left uninitialized,
	 the whole GT folder is considered -- this is the SEG's default behaviour.
	 However, occasionally a caller might want to calculate SEG only for a few
	 time points, and this when this attribute becomes useful. */
	public Set<Integer> doOnlyTheseTimepoints = null;

	/** This switches SEG to complain (and stop calculating)
	 if empty ground-truth or result image was found. */
	public boolean doStopOnEmptyImages = false;

	// ----------- the SEG essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double seg = 0.0;


	//---------------------------------------------------------------------/
	/**
	 * This is the main SEG calculator.
	 */
	public double calculate(RandomAccessibleInterval<UnsignedShortType> groudntruth, RandomAccessibleInterval<UnsignedShortType> prediction)
	{

		//instantiate the cache because it has functions we will use
		final TrackDataCache cache = new TrackDataCache();
		cache.noOfDigits = noOfDigits;

		//do the bottom stage
		//DEBUG//log.info("Computing the SEG completely...");
		seg = 0.0;
		long counter = 0;
		int imgCounter = 0;

		//if 2 dims, add a 3rd one to be processed (for loop on only one element)
		// if 2 dims, run on the 4th dimension (because that's how it is called in the compare metrics)
		int whichDim = 2;
		RandomAccessibleInterval<UnsignedShortType> gt, res;
		if(groudntruth.numDimensions() == 2){
			gt = Views.addDimension( groudntruth,0,0 );
			res = Views.addDimension( prediction,0,0 );
		} else {
			whichDim = 3;
			gt = groudntruth;
			res = prediction;
		}

		for ( int k = 0; k < gt.dimension( whichDim ); k++ )
		{
			//read the image pair
			IterableInterval< UnsignedShortType > gt_img = Views.hyperSlice( gt, whichDim, k );
					//= cache.ReadImageG16(file.toString());

			RandomAccessibleInterval<UnsignedShortType> res_img = Views.hyperSlice( res, whichDim, k );
					//= cache.ReadImageG16(String.format("%s/mask%0"+noOfDigits+"d.tif",resPath,time));


			cache.ClassifyLabels(gt_img, res_img, doStopOnEmptyImages);
			++imgCounter;

			//after ClassifyLabels(), the voxel-matching info is here:
			final TrackDataCache.TemporalLevel level = cache.levels.lastElement();

			//calculate Jaccard for matching markers at this 'level'/time point
			//if (doLogReports)
				//log.info("----------T="+time+" Z="+(slice==-1?0:slice)+"----------");

			//over all GT labels
			final int m_match_lineSize = level.m_gt_lab.length;
			for (int i=0; i < level.m_gt_lab.length; ++i)
			{
				//Jaccard for this GT label at this time point
				double acc = 0.0;

				if (level.m_gt_match[i] > -1)
				{
					//actually, we have a match,
					//update the Jaccard accordingly
					final int intersectSize
							= level.m_match[i + m_match_lineSize*level.m_gt_match[i]];

					acc  = (double)intersectSize;
					acc /= (double)level.m_gt_size[i]
							+ (double)level.m_res_size[level.m_gt_match[i]] - acc;
				}

				//update overall stats
				seg += acc;
				++counter;

				//if (doLogReports)
				//{
					//if (doAllResReports)
						//extended SEG report
						//log.info(String.format("GT_label=%d J=%.6g considered_RES_label=", level.m_gt_lab[i], acc)
								//+(level.m_gt_match[i] > -1 ? level.m_res_lab[level.m_gt_match[i]] : "-"));
					//else
						//standard SEG report
						//log.info(String.format("GT_label=%d J=%.6g", level.m_gt_lab[i], acc));
				//}
			}
		}

		seg = counter > 0 ? seg/(double)counter : 0.0;

		//log.info("---");
		//log.info("SEG: "+seg);
		return (seg);
	}


	/**
	 * Calculates pairing of/matching between the segments from the two images,
	 * and returns lists of TP and FP labels from the res_img and FN[0] count
	 * of FN labels from the gt_img. Two segments, one from the gt_img and one
	 * from the res_img, are considered matching/overlapping only if the ratio
	 * of their intersection over the area/volume of the segment from the gt_img
	 * is strictly greater than the overlapRatio parameter.
	 *
	 * The sibling method calculateDetections() with the 'cache' parameter should
	 * be used when runtime performance is considered.
	 */
	public double calculateDetections(final IterableInterval<UnsignedShortType> gt_img,
			final RandomAccessibleInterval<UnsignedShortType> res_img,
			final double overlapRatio,
			final List<Integer> TP, //LIST of good RES hits
			final List<Integer> FP, //LIST of bad RES hits
			final int[] FN)         //COUNT of missed GT ones
	{
		return calculateDetections(gt_img,res_img,null,overlapRatio,TP,FP,FN);
	}

	/**
	 * Calculates pairing of/matching between the segments from the two images,
	 * and returns lists of TP and FP labels from the res_img and FN[0] count
	 * of FN labels from the gt_img. Two segments, one from the gt_img and one
	 * from the res_img, are considered matching/overlapping only if the ratio
	 * of their intersection over the area/volume of the segment from the gt_img
	 * is strictly greater than the overlapRatio parameter.
	 *
	 * The pairing is actually established using the method class.ClassifyLabels()
	 * for which a class object should be given to be re-used. Note that the cache
	 * is always clear before any calculation takes place. If class = null then
	 * the method instantiates one internally.
	 */
	public double calculateDetections(final IterableInterval<UnsignedShortType> gt_img,
			final RandomAccessibleInterval<UnsignedShortType> res_img,
			TrackDataCache cache,
			final double overlapRatio,
			final List<Integer> TP, //LIST of good RES hits
			final List<Integer> FP, //LIST of bad RES hits
			final int[] FN)         //COUNT of missed GT ones
	{
		//if no cache is given, create one
		if (cache == null)
			cache = new TrackDataCache();

		//always use the same slot (that represents the first time point) in the cache
		final int fakeTimePoint = 0;
		cache.levels.clear();
		cache.noOfDigits = noOfDigits;

		//does the overlap-based pairing of GT and RES segments
		cache.ClassifyLabels(gt_img, res_img, doStopOnEmptyImages, fakeTimePoint, overlapRatio);

		//retrieve the "pointer" on the pairing information
		final TrackDataCache.TemporalLevel level = cache.levels.lastElement();

		double seg = 0.0;
		long counter = 0;
		int fnCnt = 0;

		//over all GT labels
		final int m_match_lineSize = level.m_gt_lab.length;
		for (int i=0; i < level.m_gt_lab.length; ++i)
		{
			//Jaccard for this GT label at this time point
			double acc = 0.0;

			if (level.m_gt_match[i] > -1)
			{
				//actually, we have a match,
				//update the Jaccard accordingly
				final int intersectSize
						= level.m_match[i + m_match_lineSize*level.m_gt_match[i]];

				acc  = (double)intersectSize;
				acc /= (double)level.m_gt_size[i]
						+ (double)level.m_res_size[level.m_gt_match[i]] - acc;
			}
			else fnCnt++;

			//update overall stats
			seg += acc;
			++counter;
		}
		if (FN.length > 0) FN[0] = fnCnt;

		//over all RES labels
		for (int j=0; j < level.m_res_lab.length; ++j)
		{
			if (level.m_res_match[j] != null && level.m_res_match[j].size() > 0)
				TP.add( level.m_res_lab[j] );
			else
				FP.add( level.m_res_lab[j] );
		}

		seg = counter > 0 ? seg/(double)counter : 0.0;
		return seg;
	}
}
