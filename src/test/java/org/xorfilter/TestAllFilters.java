package org.xorfilter;

import org.junit.Test;
import org.xorfilter.Filter;
import org.xorfilter.FilterType;
import org.xorfilter.utils.Profiler;
import org.xorfilter.utils.RandomGenerator;

/*

## Should I Use an Approximate Member Filter?

In some cases, if the false positive rate of a filter is low enough, using _just_ a filter is good enough,
an one does not need the original data altogether.
For example, a simple spell checker might just use a filter that contains
the known words. It might be OK if a mistyped word is not detected, if this is rare enough.
Another example is using a filter to reject known passwords: the complete list of all known passwords
is very large, so using a filter makes sense. The application (or user) can deal
with the possibility of false positives: the filter will simplify mark a password
as "known" even if it's not in the list.

But in most cases the original data is needed, and filters are only used to avoid unnecessary lookups.
Whether or not using a filter makes sense, and which filter to use, depends on multiple factors:

* Is it worth the additional complexity?
* How much time is saved? One has to consider the time saved by true positives,
   minus the time needed to do lookups in the filter.
   Typically, avoiding I/O make sense,
   but avoiding memory lookups usually doesn't save time.
* The memory needed by the filter often also plays a role,
   as it means less memory is available for a cache,
   and a smaller cache can slow things down.

Specially the last point makes it harder to estimate how much time can be saved by which filter type and configuration,
as many factors come into play.

To compare accurately, it might be best to write a benchmark application that is close to the real-world,
and then run this benchmark with different filters.

(Best would be to have a benchmark that simulates such an application, but it takes some time.
Or change e.g. RocksDB to use different filters.
Would it be worth it? For caching, typically "trace files" are used to compare algorithms,
but for filters this is probably harder.)

## Which Features Do I Need?

... do you need a mutable filter, do you want to store satellite data, ...

## What are the Risks?

... (I think some filters have risks, for example the cuckoo filter and other fingerprint based ones
may not be able to store an entry in rare cases, if used in the mutable way)



---------------

## Which Filter Should I Use?

For a certain false positive rate, some filter types are faster but need more memory,
others use less memory but are slower.



To decide which type to use, the average time can be estimated as follows:

* filterFpp: false positive rate of the filter (0.01 for 1%)
* applicationFpp: false positive rate of the application (how often does the application perform a lookup if the entry doesn't exist)
* filterLookupTime: average time needed by the filter to perform a lookup
* falsePositiveTime: average time needed in case of a false positive, in nanoseconds

time = (1 - applicationFpp) * filterLookupTime +
           applicationFpp * (filterLookupTime + filterFpp * falsePositiveTime)

This could be, for a LSM tree:

* applicationFpp: 0.9
* falsePositiveTime: 40000 nanoseconds (0.04 milliseconds access time for a random read in an SSD)

...





 */

public class TestAllFilters {

    public static void main(String... args) {
        testAll(1000000, true);
        System.out.println();
        testAll(10000000, true);
        System.out.println();
        testAll(100000000, true);
    }

    @Test
    public void test() {
        testAll(1000000, false);
    }

    private static void testAll(int len, boolean log) {
        for (FilterType type : FilterType.values()) {
            test(type, len, 0, log);
        }
    }

    private static void test(FilterType type, int len, int seed, boolean log) {
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len + seed);
        long[] keys = new long[len];
        long[] nonKeys = new long[len];
        // first half is keys, second half is non-keys
        for (int i = 0; i < len; i++) {
            keys[i] = list[i];
            nonKeys[i] = list[i + len];
        }
        long time = System.nanoTime();
//Profiler prof = new Profiler().startCollecting();
        Filter f = type.construct(keys, 8);
//System.out.println(prof.getTop(10));
        time = System.nanoTime() - time;
        double nanosPerAdd = time / len;
        time = System.nanoTime();
        // each key in the set needs to be found
        int falseNegatives = 0;
        for (int i = 0; i < len; i++) {
            if (!f.mayContain(keys[i])) {
                falseNegatives++;
                // f.mayContain(keys[i]);
                // throw new AssertionError();
            }
        }
        // non keys _may_ be found - this is used to calculate false
        // positives
        int falsePositives = 0;
        for (int i = 0; i < len; i++) {
            if (f.mayContain(nonKeys[i])) {
                falsePositives++;
            }
        }
        time = System.nanoTime() - time;
        double nanosPerLookup = time / 2 / len;
        double fpp = (double) falsePositives / len;
        long bitCount = f.getBitCount();
        double bitsPerKey = (double) bitCount / len;
        if (log) {
            System.out.println(type + " fpp: " + fpp +
                    " bits/key: " + bitsPerKey +
                    " add ns/key: " + nanosPerAdd +
                    " lookup ns/key: " + nanosPerLookup);
        }
        if (falseNegatives > 0) {
            throw new AssertionError("false negatives: " + falseNegatives);
        }
    }

}
