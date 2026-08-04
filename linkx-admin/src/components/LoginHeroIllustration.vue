<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="hero3d">
    <div class="hero3d-glow hero3d-glow-a" />
    <div class="hero3d-glow hero3d-glow-b" />

    <div class="hero3d-stage">
      <!-- 等距地面 -->
      <div class="hero3d-floor">
        <div class="hero3d-grid" />
      </div>

      <!-- 发光平台 -->
      <div class="hero3d-platform">
        <span class="ring ring-1" />
        <span class="ring ring-2" />
        <span class="ring ring-3" />
        <span class="ring-core" />
      </div>

      <!-- 3D 方块集群 -->
      <div class="hero3d-city">
        <div class="cube cube-a">
          <i class="f top" /><i class="f left" /><i class="f right" />
        </div>
        <div class="cube cube-b glass">
          <i class="f top" /><i class="f left" /><i class="f right" />
        </div>
        <div class="cube cube-c">
          <i class="f top" /><i class="f left" /><i class="f right" />
        </div>
        <div class="cube cube-d glass">
          <i class="f top" /><i class="f left" /><i class="f right" />
        </div>
        <div class="cube cube-e">
          <i class="f top" /><i class="f left" /><i class="f right" />
        </div>
        <div class="cube cube-f glass">
          <i class="f top" /><i class="f left" /><i class="f right" />
        </div>
      </div>

      <!-- 服务器块 -->
      <div class="hero3d-server">
        <i class="f top" /><i class="f left" /><i class="f right" />
        <span class="slot" /><span class="slot s2" /><span class="slot s3" />
      </div>

      <!-- 管道 -->
      <div class="hero3d-pipe pipe-a" />
      <div class="hero3d-pipe pipe-b" />

      <!-- 浮动玻璃卡片 -->
      <div class="float-card card-code">
        <span>&lt;/&gt;</span>
      </div>
      <div class="float-card card-user">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="8" r="4" fill="currentColor" />
          <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" fill="currentColor" />
        </svg>
      </div>
      <div class="float-card card-brand">
        <span class="mark-l">L</span><span class="mark-x">X</span>
      </div>
      <div class="float-card card-headset">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M4 12a8 8 0 0 1 16 0"
            fill="none"
            stroke="currentColor"
            stroke-width="2.4"
            stroke-linecap="round"
          />
          <rect x="2" y="11" width="4" height="7" rx="1.5" fill="currentColor" />
          <rect x="18" y="11" width="4" height="7" rx="1.5" fill="currentColor" />
          <circle cx="20" cy="20" r="2" fill="#1890ff" />
        </svg>
      </div>
    </div>

    <div class="hero3d-caption">
      <div class="hero3d-brand">
        <span class="lx-l">L</span><span class="lx-ink">ink</span><span class="lx-x">X</span>
      </div>
      <p class="hero3d-desc">{{ t('login.subtitle') }}</p>
    </div>
  </div>
</template>

<style scoped>
.hero3d {
  --cube-size: 34px;
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 16px 28px;
  box-sizing: border-box;
  overflow: hidden;
  background:
    radial-gradient(ellipse 70% 50% at 50% 42%, rgba(64, 169, 255, 0.2), transparent 60%),
    linear-gradient(165deg, #dff0ff 0%, #eef6ff 40%, #f7fbff 100%);
}

.hero3d-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
}
.hero3d-glow-a {
  width: 180px;
  height: 180px;
  top: 18%;
  left: 18%;
  background: rgba(24, 144, 255, 0.28);
}
.hero3d-glow-b {
  width: 160px;
  height: 160px;
  bottom: 22%;
  right: 12%;
  background: rgba(19, 194, 194, 0.18);
}

.hero3d-stage {
  position: relative;
  width: min(100%, 420px);
  height: min(58vh, 320px);
  perspective: 900px;
  transform-style: preserve-3d;
}

/* ── 地面 ── */
.hero3d-floor {
  position: absolute;
  left: 50%;
  top: 62%;
  width: 340px;
  height: 340px;
  margin-left: -170px;
  margin-top: -170px;
  transform: rotateX(68deg) rotateZ(-32deg);
  transform-style: preserve-3d;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(214, 233, 255, 0.75));
  border: 1px solid rgba(145, 202, 255, 0.55);
  border-radius: 18px;
  box-shadow:
    0 30px 60px rgba(24, 144, 255, 0.12),
    inset 0 0 40px rgba(255, 255, 255, 0.65);
}

.hero3d-grid {
  position: absolute;
  inset: 12px;
  border-radius: 12px;
  background-image:
    linear-gradient(rgba(24, 144, 255, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(24, 144, 255, 0.12) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(circle at 50% 50%, #000 30%, transparent 75%);
}

/* ── 光环平台 ── */
.hero3d-platform {
  position: absolute;
  left: 50%;
  top: 58%;
  width: 220px;
  height: 70px;
  margin-left: -110px;
  transform: translateZ(20px);
  display: grid;
  place-items: center;
}

.ring {
  position: absolute;
  border-radius: 50%;
  border: 5px solid transparent;
  box-shadow:
    0 0 18px rgba(24, 144, 255, 0.45),
    inset 0 0 12px rgba(105, 192, 255, 0.55);
  animation: ring-pulse 3.6s ease-in-out infinite;
}
.ring-1 {
  width: 200px;
  height: 58px;
  border-color: rgba(24, 144, 255, 0.25);
}
.ring-2 {
  width: 150px;
  height: 44px;
  border-color: rgba(64, 169, 255, 0.45);
  animation-delay: -0.6s;
}
.ring-3 {
  width: 100px;
  height: 30px;
  border-color: rgba(105, 192, 255, 0.75);
  animation-delay: -1.2s;
}
.ring-core {
  width: 36px;
  height: 12px;
  border-radius: 50%;
  background: radial-gradient(circle, #e6f7ff, #69c0ff);
  box-shadow: 0 0 24px rgba(24, 144, 255, 0.8);
}

@keyframes ring-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.85;
  }
  50% {
    transform: scale(1.04);
    opacity: 1;
  }
}

/* ── 等距立方体 ── */
.hero3d-city {
  position: absolute;
  left: 50%;
  top: 34%;
  width: 0;
  height: 0;
  transform-style: preserve-3d;
}

.cube {
  --w: 36px;
  --h: 70px;
  --d: 36px;
  position: absolute;
  width: var(--w);
  height: var(--h);
  transform-style: preserve-3d;
  transform: translate(-50%, -50%) rotateX(-18deg) rotateY(-38deg);
}

.cube .f {
  position: absolute;
  display: block;
  box-sizing: border-box;
}
.cube .top {
  width: var(--w);
  height: var(--d);
  transform: rotateX(90deg) translateZ(calc(var(--h) / 2));
  background: linear-gradient(135deg, #ffffff, #e6f4ff);
  border: 1px solid rgba(145, 202, 255, 0.55);
}
.cube .left {
  width: var(--d);
  height: var(--h);
  transform: rotateY(-90deg) translateZ(calc(var(--w) / 2));
  background: linear-gradient(180deg, #91caff, #1890ff);
  border: 1px solid rgba(24, 144, 255, 0.35);
}
.cube .right {
  width: var(--w);
  height: var(--h);
  transform: translateZ(calc(var(--d) / 2));
  background: linear-gradient(180deg, #f5faff, #d6e9ff);
  border: 1px solid rgba(145, 202, 255, 0.45);
}

.cube.glass .top {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.75), rgba(105, 192, 255, 0.35));
  backdrop-filter: blur(4px);
}
.cube.glass .left {
  background: linear-gradient(180deg, rgba(105, 192, 255, 0.65), rgba(24, 144, 255, 0.45));
}
.cube.glass .right {
  background: linear-gradient(180deg, rgba(230, 247, 255, 0.55), rgba(145, 202, 255, 0.4));
}

.cube-a {
  --w: 34px;
  --h: 86px;
  --d: 34px;
  left: -18px;
  top: 8px;
}
.cube-b {
  --w: 30px;
  --h: 110px;
  --d: 30px;
  left: 28px;
  top: -18px;
}
.cube-c {
  --w: 40px;
  --h: 64px;
  --d: 40px;
  left: -58px;
  top: 36px;
}
.cube-d {
  --w: 28px;
  --h: 78px;
  --d: 28px;
  left: 62px;
  top: 20px;
}
.cube-e {
  --w: 32px;
  --h: 52px;
  --d: 32px;
  left: 8px;
  top: 48px;
}
.cube-f {
  --w: 26px;
  --h: 96px;
  --d: 26px;
  left: -42px;
  top: -8px;
}

/* ── 服务器 ── */
.hero3d-server {
  --w: 48px;
  --h: 42px;
  --d: 48px;
  position: absolute;
  right: 42px;
  top: 58%;
  width: var(--w);
  height: var(--h);
  transform-style: preserve-3d;
  transform: rotateX(-18deg) rotateY(-38deg) translateZ(10px);
}
.hero3d-server .f {
  position: absolute;
  display: block;
}
.hero3d-server .top {
  width: var(--w);
  height: var(--d);
  transform: rotateX(90deg) translateZ(calc(var(--h) / 2));
  background: linear-gradient(135deg, #fff, #e8f3ff);
  border: 1px solid #c9dff7;
}
.hero3d-server .left {
  width: var(--d);
  height: var(--h);
  transform: rotateY(-90deg) translateZ(calc(var(--w) / 2));
  background: linear-gradient(180deg, #bae0ff, #69c0ff);
  border: 1px solid #91caff;
}
.hero3d-server .right {
  width: var(--w);
  height: var(--h);
  transform: translateZ(calc(var(--d) / 2));
  background: linear-gradient(180deg, #f8fbff, #d9ebff);
  border: 1px solid #c9dff7;
}
.hero3d-server .slot {
  position: absolute;
  left: 8px;
  top: 10px;
  width: 28px;
  height: 3px;
  border-radius: 2px;
  background: #69c0ff;
  transform: translateZ(calc(var(--d) / 2 + 1px));
  box-shadow: 0 0 6px rgba(24, 144, 255, 0.5);
}
.hero3d-server .s2 {
  top: 18px;
}
.hero3d-server .s3 {
  top: 26px;
  width: 18px;
}

/* ── 管道 ── */
.hero3d-pipe {
  position: absolute;
  border-radius: 999px;
  background: linear-gradient(180deg, #ffffff, #d6e9ff);
  border: 1px solid #c5dff8;
  box-shadow: inset 0 0 12px rgba(255, 255, 255, 0.8);
  opacity: 0.75;
}
.pipe-a {
  width: 54px;
  height: 120px;
  right: 18px;
  top: 18%;
  transform: rotateX(12deg) rotateZ(8deg);
}
.pipe-b {
  width: 40px;
  height: 90px;
  left: 24px;
  top: 42%;
  opacity: 0.45;
  transform: rotateX(10deg) rotateZ(-12deg);
}

/* ── 浮动卡片 ── */
.float-card {
  position: absolute;
  display: grid;
  place-items: center;
  border-radius: 14px;
  color: #fff;
  box-shadow:
    0 14px 28px rgba(24, 144, 255, 0.28),
    0 2px 0 rgba(255, 255, 255, 0.35) inset;
  backdrop-filter: blur(8px);
  animation: float-y 4.8s ease-in-out infinite;
}
.float-card span,
.float-card svg {
  width: 22px;
  height: 22px;
  font-size: 13px;
  font-weight: 800;
  line-height: 1;
}
.card-code {
  width: 48px;
  height: 48px;
  left: 12%;
  top: 22%;
  background: linear-gradient(145deg, #40a9ff, #096dd9);
}
.card-user {
  width: 44px;
  height: 44px;
  right: 18%;
  top: 48%;
  background: linear-gradient(145deg, #597ef7, #2f54eb);
  animation-delay: -1.2s;
}
.card-headset {
  width: 46px;
  height: 46px;
  right: 10%;
  top: 18%;
  color: #595959;
  background: linear-gradient(145deg, #ffffff, #e8f3ff);
  animation-delay: -2s;
}
.card-brand {
  width: 54px;
  height: 54px;
  left: 16%;
  top: 54%;
  background: linear-gradient(145deg, #ffffff, #f0f7ff);
  gap: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.06em;
  animation-delay: -0.7s;
}
.mark-l {
  color: #e54d42;
}
.mark-x {
  color: #fa8c16;
}

@keyframes float-y {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

/* ── 文案 ── */
.hero3d-caption {
  position: relative;
  z-index: 2;
  margin-top: 8px;
  text-align: center;
}
.hero3d-brand {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0.02em;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.lx-l,
.lx-ink {
  color: #2d7bff;
}
.lx-x {
  color: #fa8c16;
}
.hero3d-desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--lx-text-3);
}

[data-theme='dark'] .hero3d {
  background:
    radial-gradient(ellipse 70% 50% at 50% 42%, rgba(24, 144, 255, 0.22), transparent 60%),
    linear-gradient(165deg, #101820 0%, #151c28 45%, #10151c 100%);
}
[data-theme='dark'] .hero3d-floor {
  background: linear-gradient(135deg, rgba(31, 45, 61, 0.95), rgba(20, 32, 48, 0.9));
  border-color: rgba(24, 144, 255, 0.25);
}
[data-theme='dark'] .lx-l,
[data-theme='dark'] .lx-ink {
  color: #40a9ff;
}
[data-theme='dark'] .lx-x {
  color: #ffa940;
}

@media (prefers-reduced-motion: reduce) {
  .ring,
  .float-card {
    animation: none !important;
  }
}
</style>
