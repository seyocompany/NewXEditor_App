package com.example.util

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VolumeAudioProcessor(private var volume: Float = 1.0f) : AudioProcessor {
    private var pendingOutput: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var isInputEnded = false

    fun setVolume(volume: Float) {
        this.volume = volume
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return inputAudioFormat != AudioFormat.NOT_SET && volume != 1.0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val size = inputBuffer.remaining()

        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT) {
            while (inputBuffer.hasRemaining()) {
                val sample = inputBuffer.getShort()
                val scaledSample = (sample * volume).coerceIn(-32768f, 32767f).toInt().toShort()
                outputBuffer.putShort(scaledSample)
            }
        } else {
            outputBuffer.put(inputBuffer)
        }
        outputBuffer.flip()
        pendingOutput = outputBuffer
    }

    override fun queueEndOfStream() {
        isInputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = pendingOutput
        pendingOutput = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return isInputEnded && pendingOutput == AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        pendingOutput = AudioProcessor.EMPTY_BUFFER
        isInputEnded = false
    }

    override fun reset() {
        flush()
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }
}
