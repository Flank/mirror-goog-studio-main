package ${escapeKotlinIdentifiers(packageName)}

import android.net.Uri
import android.os.Bundle
import ${getMaterialComponentName('android.support.v17.leanback.app.VideoSupportFragment', useAndroidX)}
import ${getMaterialComponentName('android.support.v17.leanback.app.VideoSupportFragmentGlueHost', useAndroidX)}
import ${getMaterialComponentName('android.support.v17.leanback.media.MediaPlayerAdapter', useAndroidX)}
import ${getMaterialComponentName('android.support.v17.leanback.media.PlaybackTransportControlGlue', useAndroidX)}
import ${getMaterialComponentName('android.support.v17.leanback.widget.PlaybackControlsRow', useAndroidX)}

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: PlaybackTransportControlGlue<MediaPlayerAdapter>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (_, title, description, _, _, videoUrl) =
                activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as Movie

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        val playerAdapter = MediaPlayerAdapter(<#if minApiLevel lt 23>activity<#else>context</#if>)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = PlaybackTransportControlGlue(getActivity(), playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = title
        mTransportControlGlue.subtitle = description
        mTransportControlGlue.playWhenPrepared()

        playerAdapter.setDataSource(Uri.parse(videoUrl))
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }
}
