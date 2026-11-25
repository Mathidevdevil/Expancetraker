'use client';

import React, { useEffect, useRef, useState } from 'react';

interface CoinConfig {
  id: string;
  size: number;
  positionClass: string;
  depthZ: number;
  blurClass: string;
  opacityClass: string;
  floatAnim: 'login-coin-float-a' | 'login-coin-float-b' | 'login-coin-float-c';
  durationSec: number;
  delayMs: number;
  staticTransform: string;
}

const COINS: CoinConfig[] = [
  {
    id: 'coin-top-left',
    size: 72,
    positionClass: 'left-1 -top-12 sm:-left-16 sm:top-8 scale-[0.72] sm:scale-100',
    depthZ: 68,
    blurClass: '',
    opacityClass: 'opacity-100',
    floatAnim: 'login-coin-float-a',
    durationSec: 7.2,
    delayMs: 80,
    staticTransform: 'rotateY(-18deg) rotateX(14deg) rotateZ(-8deg)',
  },
  {
    id: 'coin-top-right',
    size: 52,
    positionClass: 'right-2 -top-9 sm:-right-12 sm:-top-5 scale-[0.75] sm:scale-100',
    depthZ: -34,
    blurClass: 'blur-[1.5px]',
    opacityClass: 'opacity-75',
    floatAnim: 'login-coin-float-b',
    durationSec: 8.8,
    delayMs: 160,
    staticTransform: 'rotateY(22deg) rotateX(-12deg) rotateZ(10deg)',
  },
  {
    id: 'coin-mid-right',
    size: 62,
    positionClass: 'hidden sm:flex -right-20 top-[46%]',
    depthZ: 54,
    blurClass: '',
    opacityClass: 'opacity-95',
    floatAnim: 'login-coin-float-c',
    durationSec: 6.4,
    delayMs: 240,
    staticTransform: 'rotateY(-24deg) rotateX(8deg) rotateZ(12deg)',
  },
  {
    id: 'coin-bottom-left',
    size: 64,
    positionClass: 'left-2 -bottom-11 sm:-left-14 sm:bottom-10 scale-[0.74] sm:scale-100',
    depthZ: 60,
    blurClass: '',
    opacityClass: 'opacity-100',
    floatAnim: 'login-coin-float-b',
    durationSec: 9.6,
    delayMs: 300,
    staticTransform: 'rotateY(18deg) rotateX(-14deg) rotateZ(-10deg)',
  },
  {
    id: 'coin-bottom-right',
    size: 46,
    positionClass: 'right-4 -bottom-9 sm:-right-10 sm:-bottom-8 scale-[0.75] sm:scale-100',
    depthZ: -52,
    blurClass: 'blur-[2px]',
    opacityClass: 'opacity-70',
    floatAnim: 'login-coin-float-a',
    durationSec: 8.1,
    delayMs: 380,
    staticTransform: 'rotateY(-14deg) rotateX(16deg) rotateZ(6deg)',
  },
];

function GoldCoin3D({ coin, reduceMotion }: { coin: CoinConfig; reduceMotion: boolean }) {
  const edgeThickness = Math.max(4, Math.round(coin.size * 0.11));
  const edgeLayers = 6;
  const fontSize = Math.round(coin.size * 0.44);

  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none select-none absolute z-20 ${coin.positionClass} ${coin.opacityClass} login-coin-stagger`}
      style={{
        width: coin.size,
        height: coin.size,
        animationDelay: `${coin.delayMs}ms`,
        transform: `translateZ(${coin.depthZ}px)`,
        transformStyle: 'preserve-3d',
      }}
    >
      <div
        className={`w-full h-full ${coin.blurClass} ${reduceMotion ? '' : coin.floatAnim}`}
        style={{
          transformStyle: 'preserve-3d',
          animationDuration: `${coin.durationSec}s`,
          animationDelay: `${coin.delayMs}ms`,
          transform: reduceMotion ? coin.staticTransform : undefined,
        }}
      >
        {/* Extruded metallic coin edge layers */}
        {Array.from({ length: edgeLayers }).map((_, idx) => {
          const zOffset = -((idx + 1) * (edgeThickness / edgeLayers));
          return (
            <div
              key={idx}
              className="absolute inset-0 rounded-full"
              style={{
                transform: `translateZ(${zOffset.toFixed(2)}px)`,
                background:
                  idx === 0
                    ? 'linear-gradient(145deg, #D4AF6A 0%, #8A6420 100%)'
                    : 'linear-gradient(145deg, #9F7427 0%, #5E4110 100%)',
                boxShadow:
                  idx === edgeLayers - 1
                    ? '0 14px 26px rgba(10, 3, 6, 0.65)'
                    : undefined,
              }}
            />
          );
        })}

        {/* Back face */}
        <div
          className="absolute inset-0 rounded-full border border-[#8A6420]"
          style={{
            transform: `translateZ(${-edgeThickness}px) rotateY(180deg)`,
            background: 'radial-gradient(circle at 35% 35%, #D4AF6A 0%, #7C5818 100%)',
          }}
        />

        {/* Front face with radial highlight, rim ring, and ₹ symbol */}
        <div
          className="absolute inset-0 rounded-full flex items-center justify-center border border-[#F3DBA0]/85"
          style={{
            background:
              'radial-gradient(circle at 30% 26%, #F3DBA0 0%, #D4AF6A 48%, #8A6420 100%)',
            boxShadow:
              'inset 0 2px 4px rgba(255, 248, 224, 0.7), inset 0 -3px 6px rgba(74, 48, 8, 0.65)',
          }}
        >
          {/* Inner recessed rim ring */}
          <div
            className="rounded-full flex items-center justify-center border border-[#8A6420]/55"
            style={{
              width: coin.size * 0.78,
              height: coin.size * 0.78,
              boxShadow:
                'inset 0 1px 3px rgba(74, 48, 8, 0.45), 0 1px 0 rgba(243, 219, 160, 0.6)',
            }}
          >
            <span
              className="font-extrabold leading-none text-[#3C2505]"
              style={{
                fontSize,
                textShadow: '0 1px 0 rgba(243, 219, 160, 0.75)',
              }}
            >
              ₹
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

export function SpendwiseSLogo3D() {
  return (
    <div
      className="w-14 h-14 rounded-[16px] flex items-center justify-center relative"
      style={{
        background: 'linear-gradient(145deg, #5A1A2E 0%, #2A0F1A 65%, #1A0810 100%)',
        border: '1.5px solid rgba(212, 175, 106, 0.85)',
        boxShadow:
          '0 18px 34px -6px rgba(10, 3, 6, 0.88), 0 0 22px rgba(212, 175, 106, 0.28), inset 0 1px 2px rgba(243, 219, 160, 0.4)',
      }}
    >
      <svg
        width="30"
        height="34"
        viewBox="0 0 30 34"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id="spendwise-s-gold" x1="4" y1="3" x2="26" y2="31" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#F3DBA0" />
            <stop offset="50%" stopColor="#D4AF6A" />
            <stop offset="100%" stopColor="#8A6420" />
          </linearGradient>
        </defs>
        <path
          d="M22.2 9.4C21.3 6.8 18.6 5.2 15.1 5.2C10.9 5.2 7.8 7.5 7.8 10.9C7.8 14.3 10.6 15.9 15.2 16.9C19.9 17.9 23.2 19.6 23.2 23.6C23.2 27.6 19.6 30.2 14.6 30.2C10.2 30.2 6.8 28.1 5.8 24.7"
          stroke="url(#spendwise-s-gold)"
          strokeWidth="4.2"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}

interface Login3DSceneProps {
  children: React.ReactNode;
}

const SLAB_LAYERS = 16;

export function Login3DScene({ children }: Login3DSceneProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [tilt, setTilt] = useState({ rx: 5, ry: -11 });
  const targetTiltRef = useRef({ rx: 5, ry: -11 });
  const currentTiltRef = useRef({ rx: 5, ry: -11 });
  const [isTabHidden, setIsTabHidden] = useState(false);
  const [reduceMotion, setReduceMotion] = useState(false);

  // Respect prefers-reduced-motion & pause when tab is hidden
  useEffect(() => {
    if (typeof window === 'undefined') return;

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const updateReduce = () => {
      setReduceMotion(mediaQuery.matches);
      if (mediaQuery.matches) {
        targetTiltRef.current = { rx: 5, ry: -11 };
        currentTiltRef.current = { rx: 5, ry: -11 };
        setTilt({ rx: 5, ry: -11 });
      }
    };
    updateReduce();
    mediaQuery.addEventListener('change', updateReduce);

    const handleVisibility = () => {
      setIsTabHidden(document.visibilityState === 'hidden');
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      mediaQuery.removeEventListener('change', updateReduce);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, []);

  // Pointer parallax tilt smoothed with a spring-like loop (max +-8 deg around resting angle)
  useEffect(() => {
    if (reduceMotion || isTabHidden) return;

    let rafId = 0;
    let vx = 0;
    let vy = 0;
    const stiffness = 0.09;
    const damping = 0.72;

    const tick = () => {
      const target = targetTiltRef.current;
      const curr = currentTiltRef.current;

      const fx = (target.rx - curr.rx) * stiffness;
      const fy = (target.ry - curr.ry) * stiffness;

      vx = (vx + fx) * damping;
      vy = (vy + fy) * damping;

      const nextRx = curr.rx + vx;
      const nextRy = curr.ry + vy;

      if (Math.abs(nextRx - curr.rx) > 0.01 || Math.abs(nextRy - curr.ry) > 0.01) {
        currentTiltRef.current = { rx: nextRx, ry: nextRy };
        setTilt({ rx: nextRx, ry: nextRy });
      }
      rafId = window.requestAnimationFrame(tick);
    };

    rafId = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(rafId);
  }, [reduceMotion, isTabHidden]);

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (reduceMotion) return;
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect || rect.width === 0 || rect.height === 0) return;

    const nx = (e.clientX - rect.left) / rect.width - 0.5; // -0.5 .. 0.5
    const ny = (e.clientY - rect.top) / rect.height - 0.5;

    // Max +/-8 degrees around resting -11deg Y and 5deg X
    targetTiltRef.current = {
      rx: 5 + -ny * 16, // range [-3, +13] -> +/-8 deg
      ry: -11 + nx * 16, // range [-19, -3] -> +/-8 deg
    };
  };

  const handlePointerLeave = () => {
    targetTiltRef.current = { rx: 5, ry: -11 };
  };

  return (
    <div
      ref={containerRef}
      onPointerMove={handlePointerMove}
      onPointerLeave={handlePointerLeave}
      className={`relative min-h-screen w-full overflow-hidden flex items-center justify-center px-4 py-16 sm:p-8 text-[#FBF5EA] ${
        isTabHidden ? 'login-3d-paused' : ''
      }`}
      style={{
        background: 'linear-gradient(160deg, #2A0F1A 0%, #1A0810 52%, #11050A 100%)',
      }}
    >
      {/* Decorative Ambient Background Glows & S-Curve (Hidden from screen readers) */}
      <div aria-hidden="true" className="pointer-events-none fixed inset-0 overflow-hidden">
        {/* Soft Plum Glow Top-Left */}
        <div
          className="absolute -top-32 -left-32 w-[480px] h-[480px] rounded-full opacity-75"
          style={{
            background: 'radial-gradient(circle, rgba(90, 26, 46, 0.72) 0%, rgba(90, 26, 46, 0) 70%)',
          }}
        />
        {/* Faint Gold Glow Bottom-Right */}
        <div
          className="absolute -bottom-36 -right-28 w-[460px] h-[460px] rounded-full opacity-50"
          style={{
            background: 'radial-gradient(circle, rgba(212, 175, 106, 0.24) 0%, rgba(212, 175, 106, 0) 70%)',
          }}
        />
        {/* Subtle Blurred S-Shaped Curve */}
        <svg
          className="absolute inset-0 w-full h-full opacity-[0.08] blur-[22px]"
          viewBox="0 0 1200 800"
          preserveAspectRatio="none"
          fill="none"
        >
          <path
            d="M 880 80 C 620 40, 420 180, 470 300 C 520 420, 740 410, 760 540 C 780 680, 560 760, 280 710"
            stroke="#D4AF6A"
            strokeWidth="76"
            strokeLinecap="round"
          />
        </svg>
      </div>

      {/* 3D Perspective Stage */}
      <div
        className="relative w-full max-w-[440px] z-10 login-card-enter"
        style={{
          perspective: '1400px',
        }}
      >
        {/* Floor Shadow beneath the 3D Card Slab */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-10 left-1/2 -translate-x-1/2 w-[90%] h-14 rounded-full blur-2xl opacity-75"
          style={{
            background: 'radial-gradient(ellipse at center, rgba(5, 1, 3, 0.92) 0%, rgba(16, 4, 9, 0.45) 55%, transparent 78%)',
          }}
        />

        {/* Tilted 3D Card + Depth Slab + Coins */}
        <div
          className="relative w-full"
          style={{
            transformStyle: 'preserve-3d',
            transform: `rotateX(${tilt.rx.toFixed(2)}deg) rotateY(${tilt.ry.toFixed(2)}deg)`,
          }}
        >
          {/* 5 Scattered 3D ₹ Gold Coins (outside the form fields) */}
          {COINS.map((coin) => (
            <GoldCoin3D key={coin.id} coin={coin} reduceMotion={reduceMotion} />
          ))}

          {/* 16 Extruded Slab Depth Layers behind Front Face (First 2 Gold, Rest Dark Plum) */}
          {Array.from({ length: SLAB_LAYERS }).map((_, idx) => {
            const layerIndex = idx + 1;
            const zDepth = -(layerIndex * 1.5);
            const isGoldEdge = layerIndex <= 2;
            const darknessMix = (layerIndex - 2) / (SLAB_LAYERS - 2);
            return (
              <div
                key={layerIndex}
                aria-hidden="true"
                className="pointer-events-none absolute inset-0 rounded-[16px]"
                style={{
                  transform: `translateZ(${zDepth.toFixed(1)}px)`,
                  background: isGoldEdge
                    ? layerIndex === 1
                      ? 'linear-gradient(145deg, #F3DBA0 0%, #D4AF6A 60%, #8A6420 100%)'
                      : 'linear-gradient(145deg, #C79B4C 0%, #8A6420 100%)'
                    : `linear-gradient(145deg, rgba(${Math.round(46 - darknessMix * 28)}, ${Math.round(
                        16 - darknessMix * 11
                      )}, ${Math.round(29 - darknessMix * 19)}, 0.99) 0%, #0E0308 100%)`,
                  border: isGoldEdge ? '1px solid rgba(243, 219, 160, 0.5)' : '1px solid rgba(26, 8, 16, 0.95)',
                }}
              />
            );
          })}

          {/* Floating Spendwise "S" Logo lifted 56px toward viewer, half outside top edge */}
          <div
            className="absolute left-1/2 -top-7 z-30 pointer-events-none"
            style={{
              transform: 'translateX(-50%) translateZ(56px)',
              transformStyle: 'preserve-3d',
            }}
          >
            <SpendwiseSLogo3D />
          </div>

          {/* Front Face: Plum Glass Gradient, Top-Left Specular Sheen, 1.5px Gold Border, Inner Glow */}
          <div
            className="relative z-10 w-full rounded-[16px] pt-11 pb-8 px-6 sm:pt-12 sm:pb-10 sm:px-10 text-[#FBF5EA]"
            style={{
              background:
                'linear-gradient(145deg, rgba(90, 26, 46, 0.78) 0%, rgba(42, 15, 26, 0.90) 52%, rgba(26, 8, 16, 0.95) 100%)',
              border: '1.5px solid rgba(212, 175, 106, 0.72)',
              boxShadow:
                'inset 1px 1px 0 rgba(243, 219, 160, 0.35), inset 0 0 28px rgba(212, 175, 106, 0.14), 0 24px 48px -12px rgba(8, 2, 5, 0.75)',
              transform: 'translateZ(0px)',
            }}
          >
            {/* Pre-blurred Top-Left Specular Sheen */}
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 rounded-[16px]"
              style={{
                background:
                  'radial-gradient(ellipse 70% 45% at 14% 8%, rgba(243, 219, 160, 0.16) 0%, rgba(243, 219, 160, 0) 70%)',
              }}
            />

            <div className="relative z-10">{children}</div>
          </div>
        </div>
      </div>
    </div>
  );
}
