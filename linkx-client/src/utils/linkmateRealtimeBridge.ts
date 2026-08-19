/**
 * 作者：yangleduo
 */
/**
 * 灵伴 Realtime 语音桥：浏览器 WebRTC 直连 OpenAI Realtime（ephemeral key）。
 */

export interface LinkMateRealtimeConnectOptions {
  ephemeralKey: string
  realtimeCallsUrl: string
  localStream: MediaStream
  onRemoteStream: (stream: MediaStream) => void
  onConnected?: () => void
  onError?: (message: string) => void
  onDisconnected?: () => void
}

export interface LinkMateRealtimeBridgeHandle {
  peerConnection: RTCPeerConnection
  dataChannel: RTCDataChannel | null
  stop: () => void
}

/**
 * 建立与 Realtime 的 WebRTC 连接，把麦克风送入模型并回传远端音轨。
 */
export async function connectLinkMateRealtime(
  options: LinkMateRealtimeConnectOptions
): Promise<LinkMateRealtimeBridgeHandle> {
  const pc = new RTCPeerConnection()
  let dataChannel: RTCDataChannel | null = null
  let stopped = false

  const stop = () => {
    if (stopped) return
    stopped = true
    try {
      dataChannel?.close()
    } catch {
      /* ignore */
    }
    dataChannel = null
    try {
      pc.close()
    } catch {
      /* ignore */
    }
  }

  pc.ontrack = evt => {
    if (stopped) return
    const stream = evt.streams[0] || new MediaStream([evt.track])
    options.onRemoteStream(stream)
  }

  pc.onconnectionstatechange = () => {
    if (stopped) return
    const state = pc.connectionState
    if (state === 'connected') {
      options.onConnected?.()
    } else if (state === 'failed') {
      options.onError?.(`Realtime 连接${state}`)
      options.onDisconnected?.()
    } else if (state === 'disconnected') {
      options.onError?.(`Realtime 连接${state}`)
      // 短暂断连可能恢复，不立刻挂断；由上层超时或用户挂断处理
    } else if (state === 'closed') {
      options.onDisconnected?.()
    }
  }

  const audioTrack = options.localStream.getAudioTracks()[0]
  if (!audioTrack) {
    stop()
    throw new Error('缺少麦克风音轨')
  }
  pc.addTrack(audioTrack, options.localStream)

  dataChannel = pc.createDataChannel('oai-events')
  dataChannel.onmessage = () => {
    /* 字幕/事件后续可解析；首期仅媒体 */
  }

  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)
  const localSdp = pc.localDescription?.sdp
  if (!localSdp) {
    stop()
    throw new Error('创建 SDP offer 失败')
  }

  const response = await fetch(options.realtimeCallsUrl, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${options.ephemeralKey}`,
      'Content-Type': 'application/sdp'
    },
    body: localSdp
  })

  if (!response.ok) {
    const errText = await response.text().catch(() => '')
    stop()
    throw new Error(errText || `Realtime SDP 交换失败 (${response.status})`)
  }

  const answerSdp = await response.text()
  if (!answerSdp.trim()) {
    stop()
    throw new Error('Realtime 未返回 SDP answer')
  }
  await pc.setRemoteDescription({ type: 'answer', sdp: answerSdp })

  return { peerConnection: pc, dataChannel, stop }
}
