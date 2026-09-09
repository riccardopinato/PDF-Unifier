package com.example

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfImageTest {
    @Test fun sampleFitsBothLandscapeAndPortraitBounds() {
        for ((width, height) in listOf(12000 to 9000, 9000 to 12000, 4033 to 3025, 10 to 10)) {
            val sample = imageSampleSize(width, height, 2200)
            assertTrue((width.toLong() + sample - 1) / sample <= 2200)
            assertTrue((height.toLong() + sample - 1) / sample <= 2200)
            assertEquals(0, sample and (sample - 1))
        }
    }
    @Test fun allExifOrientationsMapAsExpected() {
        val expected = listOf(
            floatArrayOf(2f, 3f), floatArrayOf(-2f, 3f),
            floatArrayOf(-2f, -3f), floatArrayOf(2f, -3f),
            floatArrayOf(3f, 2f), floatArrayOf(-3f, 2f),
            floatArrayOf(-3f, -2f), floatArrayOf(3f, -2f)
        )
        for (orientation in 1..8) {
            val point = floatArrayOf(2f, 3f)
            exifMatrix(orientation).mapPoints(point)
            assertArrayEquals(expected[orientation - 1], point, 0.001f)
        }
    }
    @Test fun animatedPngIsDetectedBeforeConversion() {
        val file = File.createTempFile("animated", ".png")
        try {
            RandomAccessFile(file, "rw").use {
                it.writeLong(0x89504e470d0a1a0aUL.toLong())
                it.writeInt(8)
                it.writeInt(0x6163544c)
                it.writeLong(0)
                it.writeInt(0)
            }
            assertTrue(hasAnimation(file, "image/png"))
        } finally { file.delete() }
    }
    @Test fun animatedWebpIsDetectedBeforeConversion() {
        val file = File.createTempFile("animated", ".webp")
        try {
            RandomAccessFile(file, "rw").use {
                it.writeBytes("RIFF")
                it.writeInt(Integer.reverseBytes(12))
                it.writeBytes("WEBP")
                it.writeBytes("ANIM")
                it.writeInt(0)
            }
            assertTrue(hasAnimation(file, "image/webp"))
        } finally { file.delete() }
    }
}
