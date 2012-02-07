/*
 * NormalDistribution.java
 *
 * Copyright (C) 2002-2006 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.math.distributions;

import dr.math.ErrorFunction;
import dr.math.UnivariateFunction;

/**
 * normal distribution (pdf, cdf, quantile)
 *
 * @author Korbinian Strimmer
 * @version $Id: NormalDistribution.java,v 1.7 2005/05/24 20:26:01 rambaut Exp $
 */
public class NormalDistribution implements Distribution {
    //
    // Public stuff
    //

    /**
     * Constructor
     */
    public NormalDistribution(double mean, double sd) {
        this.m = mean;
        this.sd = sd;
    }

    public double getMean() {
        return m;
    }

    public void setMean(double value) {
        m = value;
    }

    public double getSD() {
        return sd;
    }

    public void setSD(double value) {
        sd = value;
    }

    public double pdf(double x) {
        return pdf(x, m, sd);
    }

    public double logPdf(double x) {
        return logPdf(x, m, sd);
    }

    public double cdf(double x) {
        return cdf(x, m, sd);
    }

    public double quantile(double y) {
        return quantile(y, m, sd);
    }

    public double mean() {
        return mean(m, sd);
    }

    public double variance() {
        return variance(m, sd);
    }

    public final UnivariateFunction getProbabilityDensityFunction() {
        return pdfFunction;
    }

    private final UnivariateFunction pdfFunction = new UnivariateFunction() {
        public final double evaluate(double x) {
            return pdf(x);
        }

        public final double getLowerBound() {
            return Double.NEGATIVE_INFINITY;
        }

        public final double getUpperBound() {
            return Double.POSITIVE_INFINITY;
        }
    };

    /**
     * probability density function
     *
     * @param x  argument
     * @param m  mean
     * @param sd standard deviation
     * @return pdf at x
     */
    public static double pdf(double x, double m, double sd) {
        double a = 1.0 / (Math.sqrt(2.0 * Math.PI) * sd);
        double b = -(x - m) * (x - m) / (2.0 * sd * sd);

        return a * Math.exp(b);
    }

    /**
     * the natural log of the probability density function of the distribution
     *
     * @param x  argument
     * @param m  mean
     * @param sd standard deviation
     * @return log pdf at x
     */
    public static double logPdf(double x, double m, double sd) {
        double a = 1.0 / (Math.sqrt(2.0 * Math.PI) * sd);
        double b = -(x - m) * (x - m) / (2.0 * sd * sd);

        return Math.log(a) + b;
    }

    /**
     * cumulative density function
     *
     * @param x  argument
     * @param m  mean
     * @param sd standard deviation
     * @return cdf at x
     */
    public static double cdf(double x, double m, double sd) {
        double a = (x - m) / (Math.sqrt(2.0) * sd);

        return 0.5 * (1.0 + ErrorFunction.erf(a));
    }

    /**
     * quantiles (=inverse cumulative density function)
     *
     * @param z  argument
     * @param m  mean
     * @param sd standard deviation
     * @return icdf at z
     */
    public static double quantile(double z, double m, double sd) {
        return m + Math.sqrt(2.0) * sd * ErrorFunction.inverseErf(2.0 * z - 1.0);
    }

    /**
     * mean
     *
     * @param m  mean
     * @param sd standard deviation
     * @return mean
     */
    public static double mean(double m, double sd) {
        return m;
    }

    /**
     * variance
     *
     * @param m  mean
     * @param sd standard deviation
     * @return variance
     */
    public static double variance(double m, double sd) {
        return sd * sd;
    }





    /** A more accurate and faster implementation of the cdf (taken from function pnorm in the R statistical language)
     * This implementation has discrepancies depending on the programming language and system architecture
     * In Java, returned values become zero once z reaches -37.5193 exactly on the machine tested
     * In the other implementation, the returned value 0 at about z = -8
     * In C, this 0 value is reached approximately z = -37.51938
     *
     * Will later need to be optimised for BEAST
     *
     * @param x     argument
     * @param mu    mean
     * @param sigma standard deviation
     * @param log_p is p logged
     * @return cdf at x
     */
    public static double cdf(double x, double mu, double sigma, boolean log_p) {
        boolean i_tail=false;
        double p, cp = Double.NaN;
        
        if(Double.isNaN(x) || Double.isNaN(mu) || Double.isNaN(sigma)) {
            return Double.NaN;
        }
        if(Double.isInfinite(x) && mu == x) { /* x-mu is NaN */
            return Double.NaN;
        }
        if (sigma <= 0) {
            if(sigma < 0) {
                return Double.NaN;
            }
            return (x < mu) ? 0.0 : 1.0;
        }
        p = (x - mu) / sigma;
        if(Double.isInfinite(p)) {
            return (x < mu) ? 0.0 : 1.0;
        }
        x = p;
        if(Double.isNaN(x)) {
            return Double.NaN;
        }

        double xden, xnum, temp, del, eps, xsq, y;
        int i;
        boolean lower, upper;
        eps = DBL_EPSILON * 0.5;
        lower = !i_tail;
        upper = i_tail;

        y = Math.abs(x);
        if (y <= 0.67448975) { /* Normal.quantile(3/4, 1, 0) = 0.67448975 */
            if (y > eps) {
                xsq = x * x;
                xnum = a[4] * xsq;
                xden = xsq;
                for (i = 0; i < 3; i++) {
                    xnum = (xnum + a[i]) * xsq;
                    xden = (xden + b[i]) * xsq;
                }
            }
            else {
                xnum = xden = 0.0;
            }
            temp = x * (xnum + a[3]) / (xden + b[3]);
            if(lower) {
                p = 0.5 + temp;
            }
            if(upper) {
                cp = 0.5 - temp;
            }
            if(log_p) {
                if(lower) {
                    p = Math.log(p);
                }
                if(upper) {
                    cp = Math.log(cp);
                }
            }
        }


        else if (y <= M_SQRT_32) {
            /* Evaluate pnorm for 0.67448975 = Normal.quantile(3/4, 1, 0) < |x| <= sqrt(32) ~= 5.657 */

            xnum = c[8] * y;
            xden = y;
            for (i = 0; i < 7; i++) {
                xnum = (xnum + c[i]) * y;
                xden = (xden + d[i]) * y;
            }
            temp = (xnum + c[7]) / (xden + d[7]);

            //do_del(y);
            //swap_tail;
            //#define do_del(X)							\
            xsq = ((int) (y * CUTOFF)) * 1.0 / CUTOFF;
            del = (y - xsq) * (y + xsq);
            if(log_p) {
                p = (-xsq * xsq * 0.5) + (-del * 0.5) + Math.log(temp);
                if((lower && x > 0.0) || (upper && x <= 0.0)) {
                  cp = Math.log(1.0-Math.exp(-xsq * xsq * 0.5) * Math.exp(-del * 0.5) * temp);
                }
            }
            else {
                p = Math.exp(-xsq * xsq * 0.5) * Math.exp(-del * 0.5) * temp;
                cp = 1.0 - p;
            }
            //#define swap_tail						\
            if (x > 0.0) {
                temp = p;
                if(lower) {
                    p = cp;
                }
                cp = temp;
            }
        }
        /* else	  |x| > sqrt(32) = 5.657 :
         * the next two case differentiations were really for lower=T, log=F
         * Particularly	 *not*	for  log_p !
         * Cody had (-37.5193 < x  &&  x < 8.2924) ; R originally had y < 50
         * Note that we do want symmetry(0), lower/upper -> hence use y
         */
        else if(log_p || (lower && -37.5193 < x  &&  x < 8.2924)
            || (upper && -8.2924  < x  &&  x < 37.5193)) {

            /* Evaluate pnorm for x in (-37.5, -5.657) union (5.657, 37.5) */
            xsq = 1.0 / (x * x);
            xnum = p_[5] * xsq;
            xden = xsq;
            for (i = 0; i < 4; i++) {
                xnum = (xnum + p_[i]) * xsq;
                xden = (xden + q[i]) * xsq;
            }
            temp = xsq * (xnum + p_[4]) / (xden + q[4]);
            temp = (M_1_SQRT_2PI - temp) / y;

            //do_del(x);
            xsq = ((int) (x * CUTOFF)) * 1.0 / CUTOFF;
            del = (x - xsq) * (x + xsq);
            if(log_p) {
                p = (-xsq * xsq * 0.5) + (-del * 0.5) + Math.log(temp);
                if((lower && x > 0.0) || (upper && x <= 0.0)) {
                  cp = Math.log(1.0-Math.exp(-xsq * xsq * 0.5) * Math.exp(-del * 0.5) * temp);
                }
            }
            else {
                p = Math.exp(-xsq * xsq * 0.5) * Math.exp(-del * 0.5) * temp;
                cp = 1.0 - p;
            }
            //swap_tail;
            if (x > 0.0) {
                temp = p;
                if(lower) {
                    p = cp;
                }
                cp = temp;
            }
        }
        else { /* no log_p , large x such that probs are 0 or 1 */
            if(x > 0) {
                p = 1.0;
                cp = 0.0;
            }
            else {
                p = 0.0;
                cp = 1.0;
            }
        }
        return p;

    }

    // Private

    protected double m, sd;

    private static final double[] a = {
        2.2352520354606839287,
        161.02823106855587881,
        1067.6894854603709582,
        18154.981253343561249,
        0.065682337918207449113
    };
    private static final double[] b = {
        47.20258190468824187,
        976.09855173777669322,
        10260.932208618978205,
        45507.789335026729956
    };
    private static final double[] c = {
        0.39894151208813466764,
        8.8831497943883759412,
        93.506656132177855979,
        597.27027639480026226,
        2494.5375852903726711,
        6848.1904505362823326,
        11602.651437647350124,
        9842.7148383839780218,
        1.0765576773720192317e-8
    };
    private static final double[] d = {
        22.266688044328115691,
        235.38790178262499861,
        1519.377599407554805,
        6485.558298266760755,
        18615.571640885098091,
        34900.952721145977266,
        38912.003286093271411,
        19685.429676859990727
    };
    private static final double[] p_ = {
        0.21589853405795699,
        0.1274011611602473639,
        0.022235277870649807,
        0.001421619193227893466,
        2.9112874951168792e-5,
        0.02307344176494017303
    };
    private static final double[] q = {
        1.28426009614491121,
        0.468238212480865118,
        0.0659881378689285515,
        0.00378239633202758244,
        7.29751555083966205e-5
    };

    private static final int CUTOFF = 16; /* Cutoff allowing exact "*" and "/" */

    private static final double M_SQRT_32 = 5.656854249492380195206754896838; /* The square root of 32 */
    private static final double M_1_SQRT_2PI =  0.398942280401432677939946059934;
    private static final double DBL_EPSILON = 2.2204460492503131e-016;
}