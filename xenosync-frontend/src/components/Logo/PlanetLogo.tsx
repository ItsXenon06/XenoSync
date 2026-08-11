import { useEffect, useRef } from 'react';
import './PlanetLogo.css';

const BASE = 340; // the scene is designed at 340px and scaled to fit `size`
const COLORS = [
    '#8FCBFF', '#5FA8FF', '#3B82F6', '#FF8A3D', '#FFC177',
    '#FFD966', '#4ADE80', '#F472B6', '#A78BFA', '#F87171', '#2DD4BF',
];
const RX = 152;
const RY = 60;

interface Cursor {
    el: HTMLDivElement;
    angle: number;
    speed: number;
    radiusScale: number;
    life: number;
    fadeState: 'in' | 'active' | 'out';
    fadeTimer: number;
    lifespan: number;
}

function cursorSVG(color: string) {
    return `<svg viewBox="0 0 24 24" fill="none">
      <path d="M4 3L20 11L12.5 12.5L11 20L4 3Z" fill="${color}" stroke="rgba(255,255,255,0.55)" stroke-width="1" stroke-linejoin="round"/>
    </svg>`;
}

interface PlanetLogoProps {
    /** Rendered diameter in px. */
    size?: number;
    /** Orbiting collaborator cursors. Turn off for small placements (nav bars, favicons). */
    animated?: boolean;
    /** Click-to-pause the orbit. Only meaningful when animated. */
    interactive?: boolean;
    className?: string;
}

export default function PlanetLogo({
                                       size = 112,
                                       animated = true,
                                       interactive = true,
                                       className = '',
                                   }: PlanetLogoProps) {
    const layerRef = useRef<HTMLDivElement>(null);
    const cursorsRef = useRef<Cursor[]>([]);
    const pausedRef = useRef(false);
    const rafRef = useRef<number | null>(null);

    useEffect(() => {
        if (!animated || !layerRef.current) return;
        const layer = layerRef.current;

        function spawnCursor() {
            const el = document.createElement('div');
            el.className = 'planet-logo-cursor';
            el.style.opacity = '0';
            el.innerHTML = cursorSVG(COLORS[Math.floor(Math.random() * COLORS.length)]);
            layer.appendChild(el);
            cursorsRef.current.push({
                el,
                angle: Math.random() * Math.PI * 2,
                speed: (0.25 + Math.random() * 0.35) * (Math.random() < 0.5 ? 1 : -1),
                radiusScale: 0.75 + Math.random() * 0.35,
                life: 0,
                fadeState: 'in',
                fadeTimer: 0,
                lifespan: 8000 + Math.random() * 9000,
            });
        }

        function despawn(c: Cursor) {
            c.fadeState = 'out';
            c.fadeTimer = 0;
        }

        function maintainPopulation() {
            if (cursorsRef.current.filter((c) => c.fadeState !== 'out').length < 2) spawnCursor();
        }

        for (let i = 0; i < 3; i++) spawnCursor();

        let last = performance.now();

        function tick(now: number) {
            const dt = Math.min(now - last, 50);
            last = now;
            const paused = pausedRef.current;

            if (!paused) {
                cursorsRef.current.forEach((c) => {
                    c.angle += (c.speed * dt) / 1000;
                    c.life += dt;

                    if (c.fadeState === 'in') {
                        c.fadeTimer += dt;
                        if (c.fadeTimer > 500) c.fadeState = 'active';
                    } else if (c.fadeState === 'active') {
                        const activeCount = cursorsRef.current.filter((x) => x.fadeState !== 'out').length;
                        if (c.life > c.lifespan && activeCount > 2 && Math.random() < 0.02) despawn(c);
                        if (activeCount < 5 && Math.random() < 0.0035) spawnCursor();
                    } else if (c.fadeState === 'out') {
                        c.fadeTimer += dt;
                    }
                });
            }

            cursorsRef.current.forEach((c) => {
                const rx = RX * c.radiusScale;
                const ry = RY * c.radiusScale;
                const x = Math.cos(c.angle) * rx;
                const y = Math.sin(c.angle) * ry;
                const depth = Math.sin(c.angle);
                const scale = 0.55 + 0.55 * ((depth + 1) / 2);
                const baseOpacity = 0.35 + 0.65 * ((depth + 1) / 2);

                let fadeMul = 1;
                if (c.fadeState === 'in') fadeMul = Math.min(1, c.fadeTimer / 500);
                if (c.fadeState === 'out') fadeMul = Math.max(0, 1 - c.fadeTimer / 500);

                c.el.style.transform = `translate(${x - 10}px, ${y - 10}px) scale(${scale.toFixed(2)})`;
                c.el.style.opacity = (baseOpacity * fadeMul).toFixed(2);
                c.el.style.zIndex = depth > 0 ? '250' : '90';
            });

            cursorsRef.current = cursorsRef.current.filter((c) => {
                if (c.fadeState === 'out' && c.fadeTimer > 500) {
                    c.el.remove();
                    return false;
                }
                return true;
            });

            if (!pausedRef.current) maintainPopulation();
            rafRef.current = requestAnimationFrame(tick);
        }

        rafRef.current = requestAnimationFrame(tick);

        return () => {
            if (rafRef.current) cancelAnimationFrame(rafRef.current);
            cursorsRef.current.forEach((c) => c.el.remove());
            cursorsRef.current = [];
        };
    }, [animated]);

    const scale = size / BASE;

    return (
        <div className={`planet-logo-wrap ${className}`} style={{ width: size, height: size }}>
            <div
                className="planet-logo-stage"
                style={{ transform: `translate(-50%, -50%) scale(${scale})` }}
                onClick={() => {
                    if (interactive && animated) pausedRef.current = !pausedRef.current;
                }}
            >
                <div className="planet-logo-guide" />
                <div className="planet-logo-guide inner" />
                <div className="planet-logo-planet-wrap">
                    <div className="planet-logo-ring ring2" />
                    <div className="planet-logo-ring" />
                    <div className="planet-logo-planet" />
                </div>
                {animated && <div ref={layerRef} className="planet-logo-cursor-layer" />}
            </div>
        </div>
    );
}