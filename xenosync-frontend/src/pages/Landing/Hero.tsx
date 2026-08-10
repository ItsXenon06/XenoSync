import { useEffect, useRef, useState } from 'react';
import HeroMock from './HeroMock';

const WORDS = ['eventually', 'solo', 'from a call', 'in silos'];
const EDITORS = [
    { name: 'Kost', color: 'var(--user-k)', dark: true },
    { name: 'Mira', color: 'var(--user-m)', dark: false },
    { name: 'Reo', color: 'var(--user-r)', dark: false },
];

export default function Hero({ onSignup }: { onSignup: () => void }) {
    const [wIdx, setWIdx] = useState(0);
    const [displayed, setDisplayed] = useState('');
    const [struck, setStruck] = useState(false);
    const [liveCount, setLiveCount] = useState(214);
    const phaseTimeout = useRef<number | null>(null);

    const editor = EDITORS[wIdx % EDITORS.length];

    // typing/erasing cycle for the headline word
    useEffect(() => {
        let cancelled = false;
        const word = WORDS[wIdx % WORDS.length];
        setDisplayed('');
        setStruck(false);

        let i = 0;
        const typeInterval = setInterval(() => {
            if (cancelled) return;
            i++;
            setDisplayed(word.slice(0, i));
            if (i >= word.length) {
                clearInterval(typeInterval);
                phaseTimeout.current = window.setTimeout(() => {
                    setStruck(true);
                    phaseTimeout.current = window.setTimeout(() => {
                        setStruck(false);
                        let j = word.length;
                        const eraseInterval = setInterval(() => {
                            if (cancelled) return;
                            j--;
                            setDisplayed(word.slice(0, j));
                            if (j <= 0) {
                                clearInterval(eraseInterval);
                                setWIdx((prev) => prev + 1);
                            }
                        }, 38);
                    }, 750);
                }, 1500);
            }
        }, 65);

        return () => {
            cancelled = true;
            clearInterval(typeInterval);
            if (phaseTimeout.current) clearTimeout(phaseTimeout.current);
        };
    }, [wIdx]);

    // fake live session counter
    useEffect(() => {
        const t = setInterval(() => {
            setLiveCount((c) => {
                const next = c + (Math.random() > 0.5 ? 1 : -1) * Math.ceil(Math.random() * 3);
                return next < 190 ? 190 : next;
            });
        }, 2600);
        return () => clearInterval(t);
    }, []);

    return (
        <section className="hero">
            <div className="wrap hero-inner">
                <div className="eyebrow">
                    <span className="dot" /> <span>{liveCount}</span> sessions running right now
                </div>

                <h1>
                    Code together.<br />
                    Not{' '}
                    <span className="edit-wrap">
            <span
                className="name-chip"
                style={{ background: editor.color, color: editor.dark ? '#0c0820' : '#fff' }}
            >
              {editor.name}
            </span>
            <span className={`edit-word${struck ? ' struck' : ''}`}>{displayed}</span>
            <span className="edit-caret" style={{ background: editor.color }} />
          </span>
                    .
                </h1>

                <p className="sub">
                    XenoSync is a shared workspace where your team edits, chats, runs, and ships — Spring
                    Boot, .NET, Node, or Python — without ever leaving the browser tab.
                </p>

                <div className="hero-ctas">
                    <a className="btn btn-primary btn-lg" onClick={onSignup}>Start a session free</a>
                    <a className="btn btn-ghost btn-lg" href="#features">See how it works</a>
                </div>

                <HeroMock />
            </div>
        </section>
    );
}