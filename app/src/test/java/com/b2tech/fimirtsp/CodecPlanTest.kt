package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.settings.VideoCodecPref
import com.b2tech.fimirtsp.streaming.BitrateMode
import com.b2tech.fimirtsp.streaming.CodecPlan
import com.b2tech.fimirtsp.streaming.EncoderCandidate
import com.b2tech.fimirtsp.streaming.EncoderSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecPlanTest {

    @Test fun h265_tenta_hevc_e_depois_h264() {
        assertEquals(
            listOf(VideoCodecPref.H265, VideoCodecPref.H264),
            CodecPlan.attemptOrder(VideoCodecPref.H265),
        )
    }

    @Test fun h264_nunca_sobe_para_h265() {
        assertEquals(listOf(VideoCodecPref.H264), CodecPlan.attemptOrder(VideoCodecPref.H264))
        assertEquals(VideoCodecPref.H264, CodecPlan.expected(VideoCodecPref.H264, hevcAvailable = true))
    }

    @Test fun h265_sem_encoder_sai_em_h264() {
        assertEquals(VideoCodecPref.H264, CodecPlan.expected(VideoCodecPref.H265, hevcAvailable = false))
    }

    @Test fun h265_com_encoder_sai_em_h265() {
        assertEquals(VideoCodecPref.H265, CodecPlan.expected(VideoCodecPref.H265, hevcAvailable = true))
    }

    @Test fun instalacao_antiga_ou_valor_desconhecido_fica_em_h264() {
        assertEquals(VideoCodecPref.H264, VideoCodecPref.fromName(null))
        assertEquals(VideoCodecPref.H264, VideoCodecPref.fromName("AV1"))
        assertEquals(VideoCodecPref.H265, VideoCodecPref.fromName("H265"))
    }

    private val hwHevcVbr = EncoderCandidate("c2.qti.hevc.encoder", hardware = true, supportsCbr = false, supportsSurface = true)
    private val swHevcCbr = EncoderCandidate("c2.android.hevc.encoder", hardware = false, supportsCbr = true, supportsSurface = true)

    @Test fun h265_nunca_usa_software_mesmo_com_cbr() {
        val pick = EncoderSelection.pick(CodecPlan.eligible(VideoCodecPref.H265, listOf(swHevcCbr, hwHevcVbr)))
        assertEquals("c2.qti.hevc.encoder", pick?.name)
        assertEquals(BitrateMode.VBR, pick?.mode)
    }

    @Test fun h265_so_com_software_conta_como_sem_encoder() {
        assertNull(EncoderSelection.pick(CodecPlan.eligible(VideoCodecPref.H265, listOf(swHevcCbr))))
    }

    @Test fun h264_mantem_software_cbr_na_frente_do_hardware_vbr() {
        val swAvcCbr = swHevcCbr.copy(name = "c2.android.avc.encoder")
        val hwAvcVbr = hwHevcVbr.copy(name = "c2.qti.avc.encoder")
        val pick = EncoderSelection.pick(CodecPlan.eligible(VideoCodecPref.H264, listOf(hwAvcVbr, swAvcCbr)))
        assertEquals("c2.android.avc.encoder", pick?.name)
        assertEquals(BitrateMode.CBR, pick?.mode)
    }

    @Test fun mime_bate_com_o_mediacodec() {
        assertEquals("video/avc", VideoCodecPref.H264.mime)
        assertEquals("video/hevc", VideoCodecPref.H265.mime)
    }
}
