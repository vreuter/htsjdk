/*
 * Copyright (c) 2010, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.tribble.bed;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.annotation.Strand;
import htsjdk.tribble.bed.FullBEDFeature.Exon;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.tribble.util.ParsingUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class BEDCodecTest extends HtsjdkTest {

    @DataProvider(name = "gzippedBedTestData")
    public Object[][] getBedTestData(){
        return new Object[][] {
                {
                    // BGZP BED file with no header, 2 features
                    new File(TestUtils.DATA_DIR, "bed/2featuresNoHeader.bed.gz"), 0  // header has length 0
                },
                {
                    // BGZP BED file with one line header, 2 features
                    new File(TestUtils.DATA_DIR, "bed/2featuresWithHeader.bed.gz"), 10 // header has length 10

                }
        };
    }

    @Test(dataProvider = "gzippedBedTestData")
    public void testReadActualHeader(final File gzippedBedFile, final int firstFeatureOffset) throws IOException {
        // Given an indexable SOURCE on a BED file, test that readActualHeader retains the correct offset
        // of the first feature, whether there is a header or not
        BEDCodec bedCodec = new BEDCodec();
        try (final InputStream is = ParsingUtils.openInputStream(gzippedBedFile.getPath());
             final BlockCompressedInputStream bcis = new BlockCompressedInputStream(is))
        {
            AsciiLineReaderIterator it = (AsciiLineReaderIterator) bedCodec.makeIndexableSourceFromStream(bcis);
            Object header = bedCodec.readActualHeader(it);
            // BEDCodec doesn't model or return the BED header, even when there is one!
            Assert.assertNull(header);
            Assert.assertEquals(BlockCompressedFilePointerUtil.getBlockAddress(it.getPosition()), 0);
            Assert.assertEquals(BlockCompressedFilePointerUtil.getBlockOffset(it.getPosition()), firstFeatureOffset);
        }
    }

    @Test
    public void testSimpleDecode() {
        BEDCodec codec = new BEDCodec();

        BEDFeature feature;

        feature = codec.decode("chr1 1");
        Assert.assertEquals(feature.getContig(), "chr1");
        Assert.assertEquals(feature.getStart(), 2);
        Assert.assertEquals(feature.getEnd(), 2);

        feature = codec.decode("chr1 1 2");
        Assert.assertEquals(feature.getContig(), "chr1");
        Assert.assertEquals(feature.getStart(), 2);
        Assert.assertEquals(feature.getEnd(), 2);

        feature = codec.decode("chr1 1 3");
        Assert.assertEquals(feature.getContig(), "chr1");
        Assert.assertEquals(feature.getStart(), 2);
        Assert.assertEquals(feature.getEnd(), 3);
    }

    @Test
    public void testFullDecode() {
        BEDCodec codec = new BEDCodec();

        FullBEDFeature feature;
        List<Exon> exons;

        // Borrowed samples from Example: on http://genome.ucsc.edu/FAQ/FAQformat#format1

        feature = (FullBEDFeature) codec.decode("chr22 1000 5000 cloneA 960 + 1000 5000 0 2 567,488, 0,3512");
        Assert.assertEquals(feature.getContig(), "chr22");
        Assert.assertEquals(feature.getStart(), 1001);
        Assert.assertEquals(feature.getEnd(), 5000);
        Assert.assertEquals(feature.getName(), "cloneA");
        Assert.assertEquals(feature.getScore(), 960f);
        Assert.assertEquals(feature.getStrand(), Strand.POSITIVE);
        Assert.assertEquals(feature.getColor(), new Color(0));

        exons = feature.getExons();
        Assert.assertEquals(exons.size(), 2);

        Assert.assertEquals(exons.get(0).getNumber(), 1);
        Assert.assertEquals(exons.get(0).start, 1001);
        Assert.assertEquals(exons.get(0).end, 1567);
        Assert.assertEquals(exons.get(0).getCdStart(), 1001);
        Assert.assertEquals(exons.get(0).getCdEnd(), 1567);
        Assert.assertEquals(exons.get(0).getCodingLength(), 567);

        Assert.assertEquals(exons.get(1).getNumber(), 2);
        Assert.assertEquals(exons.get(1).start, 4513);
        Assert.assertEquals(exons.get(1).end, 5000);
        Assert.assertEquals(exons.get(1).getCdStart(), 4513);
        Assert.assertEquals(exons.get(1).getCdEnd(), 5000);
        Assert.assertEquals(exons.get(1).getCodingLength(), 488);

        feature = (FullBEDFeature) codec.decode("chr22 2000 6000 cloneB 900 - 2000 6000 0 2 433,399, 0,3601");
        Assert.assertEquals(feature.getContig(), "chr22");
        Assert.assertEquals(feature.getStart(), 2001);
        Assert.assertEquals(feature.getEnd(), 6000);
        Assert.assertEquals(feature.getName(), "cloneB");
        Assert.assertEquals(feature.getScore(), 900f);
        Assert.assertEquals(feature.getStrand(), Strand.NEGATIVE);
        Assert.assertEquals(feature.getColor(), new Color(0));

        exons = feature.getExons();
        Assert.assertEquals(exons.size(), 2);

        Assert.assertEquals(exons.get(0).getNumber(), 2);
        Assert.assertEquals(exons.get(0).start, 2001);
        Assert.assertEquals(exons.get(0).end, 2433);
        Assert.assertEquals(exons.get(0).getCdStart(), 2001);
        Assert.assertEquals(exons.get(0).getCdEnd(), 2433);
        Assert.assertEquals(exons.get(0).getCodingLength(), 433);

        Assert.assertEquals(exons.get(1).getNumber(), 1);
        Assert.assertEquals(exons.get(1).start, 5602);
        Assert.assertEquals(exons.get(1).end, 6000);
        Assert.assertEquals(exons.get(1).getCdStart(), 5602);
        Assert.assertEquals(exons.get(1).getCdEnd(), 6000);
        Assert.assertEquals(exons.get(1).getCodingLength(), 399);
    }

    @Test
    public void testDecodeBEDFile_good() throws Exception {
        String filepath = TestUtils.DATA_DIR + "bed/NA12878.deletions.10kbp.het.gq99.hand_curated.hg19_fixed.bed";
        int expected_lines = 34;
        /*
        Line 0:
        1	25592413	25657872
        Line 3:
        1	152555536	152587611
        Line 28:
        14	73996607	74025282
        Remember tribble increments numbers by 1
         */

        BEDCodec codec = new BEDCodec();

        AbstractFeatureReader reader = AbstractFeatureReader.getFeatureReader(filepath, codec, false);

        Iterable<Feature> iter = reader.iterator();
        int count = 0;
        for (Feature feat : iter) {
            Assert.assertTrue(feat.getContig().length() > 0);
            Assert.assertTrue(feat.getEnd() >= feat.getStart());

            if (count == 0) {
                Assert.assertEquals("1", feat.getContig());
                Assert.assertEquals(25592413 + 1, feat.getStart());
                Assert.assertEquals(25657872, feat.getEnd());
            }

            if (count == 3) {
                Assert.assertEquals("1", feat.getContig());
                Assert.assertEquals(152555536 + 1, feat.getStart());
                Assert.assertEquals(152587611, feat.getEnd());
            }

            if (count == 28) {
                Assert.assertEquals("14", feat.getContig());
                Assert.assertEquals(73996607 + 1, feat.getStart());
                Assert.assertEquals(74025282, feat.getEnd());
            }

            count += 1;
        }

        Assert.assertEquals(expected_lines, count);

        reader.close();

    }

    /**
     * Test reading a BED file which is malformed.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = RuntimeException.class)
    public void testDecodeBEDFile_bad() throws Exception {
        //This file has an extra tab in the second to last line
        String filepath = TestUtils.DATA_DIR + "bed/NA12878.deletions.10kbp.het.gq99.hand_curated.hg19.bed";
        //The iterator implementation next() actually performs a get / read_next. The bad line is number 32,
        //so we actually will only get 31 lines before reading that line.
        int expected_count = 31;
        BEDCodec codec = new BEDCodec();

        AbstractFeatureReader reader = AbstractFeatureReader.getFeatureReader(filepath, codec, false);

        Iterable<Feature> iter = reader.iterator();
        int count = 0;
        for (Feature feat : iter) {
            count += 1;
        }
        reader.close();
    }

    @Test
    public void testGetTabixFormat() {
        Assert.assertEquals(new BEDCodec().getTabixFormat(), TabixFormat.BED);
    }

    @Test
    public void testCanDecode() {
        final BEDCodec codec = new BEDCodec();
        final String pattern = "filename.%s%s";
        for(final String bcExt: FileExtensions.BLOCK_COMPRESSED) {
            Assert.assertTrue(codec.canDecode(String.format(pattern, "bed", bcExt)));
            Assert.assertFalse(codec.canDecode(String.format(pattern, "vcf", bcExt)));
            Assert.assertFalse(codec.canDecode(String.format(pattern, "bed.gzip", bcExt)));
        }
    }
}
