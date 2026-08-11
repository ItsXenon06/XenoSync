import { useEffect, useState } from 'react';
import './Landing.css';
import Hero from './Hero';
import FeatureBento from './FeatureBento';
import CtaBand from './CtaBand';
import AuthModal from './AuthModal';
import PlanetLogo from '../../components/Logo/PlanetLogo';

const STACKS = ['Spring Boot', '.NET', 'Node.js', 'Python', 'PostgreSQL', 'MySQL', 'GitHub', 'Go', 'Redis'];

export default function Landing() {
    const [authOpen, setAuthOpen] = useState(false);
    const [authMode, setAuthMode] = useState<'login' | 'signup'>('login');
    const [theme, setTheme] = useState<'dark' | 'light'>(
        (document.documentElement.getAttribute('data-theme') as 'dark' | 'light') || 'dark'
    );

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
    }, [theme]);

    function openAuth(mode: 'login' | 'signup') {
        setAuthMode(mode);
        setAuthOpen(true);
    }

    return (
        <div className="landing">
            {/* background layers */}
            <div className="stars" aria-hidden="true">
                {Array.from({ length: 70 }).map((_, i) => (
                    <span
                        key={i}
                        className="star"
                        style={{
                            left: `${Math.random() * 100}%`,
                            top: `${Math.random() * 100}%`,
                            animationDelay: `${Math.random() * 4}s`,
                        }}
                    />
                ))}
            </div>

            <div className="ocean-bg" aria-hidden="true">
                <div className="ocean-blob blob1" />
                <div className="ocean-blob blob2" />
                <div className="ocean-blob blob3" />
                <div className="ocean-blob blob4" />
                <div>
                    {Array.from({ length: 100 }).map((_, i) => {
                        const size = 4 + Math.random() * 18;
                        return (
                            <div
                                key={i}
                                className="bubble"
                                style={{
                                    width: size,
                                    height: size,
                                    left: `${Math.random() * 100}%`,
                                    animationDuration: `${9 + Math.random() * 10}s`,
                                    animationDelay: `${Math.random() * 12}s`,
                                }}
                            />
                        );
                    })}
                </div>
                <div className="ocean-waves">
                    <div className="wave-track slow">
                        <svg viewBox="0 0 1000 200" preserveAspectRatio="none">
                            <path d="M0,90 C150,150 350,30 500,90 C650,150 850,30 1000,90 L1000,200 L0,200 Z" fill="#0577B8" opacity="0.22" />
                        </svg>
                        <svg viewBox="0 0 1000 200" preserveAspectRatio="none">
                            <path d="M0,90 C150,150 350,30 500,90 C650,150 850,30 1000,90 L1000,200 L0,200 Z" fill="#0577B8" opacity="0.22" />
                        </svg>
                    </div>
                    <div className="wave-track">
                        <svg viewBox="0 0 1000 200" preserveAspectRatio="none">
                            <path d="M0,110 C170,60 330,150 500,110 C670,60 830,150 1000,110 L1000,200 L0,200 Z" fill="#12B4C9" opacity="0.28" />
                        </svg>
                        <svg viewBox="0 0 1000 200" preserveAspectRatio="none">
                            <path d="M0,110 C170,60 330,150 500,110 C670,60 830,150 1000,110 L1000,200 L0,200 Z" fill="#12B4C9" opacity="0.28" />
                        </svg>
                    </div>
                </div>
            </div>

            <header>
                <nav>
                    <div className="brand">
                        <PlanetLogo size={28} animated={false} interactive={false} />
                        <span className="brand-name">
                            <span>Xeno</span><span>Sync</span>
                        </span>
                    </div>
                    <div className="nav-links">
                        <a href="#features">Features</a>
                        <a href="#run">Run &amp; Database</a>
                        <a href="#collab">Collaboration</a>
                    </div>
                    <div className="nav-actions">
                        <div
                            className="theme-toggle"
                            onClick={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))}
                        >
                            <div className="knob">{theme === 'dark' ? '✦' : '☀'}</div>
                        </div>
                        <a className="btn btn-ghost" onClick={() => openAuth('login')}>Log in</a>
                        <a className="btn btn-primary" onClick={() => openAuth('signup')}>Start free</a>
                    </div>
                </nav>
            </header>

            <Hero onSignup={() => openAuth('signup')} />

            <div className="marquee-band">
                <div className="marquee-track">
                    {[...STACKS, ...STACKS, ...STACKS, ...STACKS].map((s, i) => (
                        <div className="marquee-item" key={i}><b>●</b> {s}</div>
                    ))}
                </div>
            </div>

            <FeatureBento />

            <CtaBand onSignup={() => openAuth('signup')} />

            <footer>
                <div className="wrap footer-row">
                    <div>© 2026 XenoSync</div>
                    <div className="footer-links">
                        <a href="#">Privacy</a><a href="#">Terms</a><a href="#">Status</a><a href="#">Contact</a>
                    </div>
                </div>
            </footer>

            <AuthModal
                open={authOpen}
                mode={authMode}
                onClose={() => setAuthOpen(false)}
                onSwitchMode={setAuthMode}
            />
        </div>
    );
}