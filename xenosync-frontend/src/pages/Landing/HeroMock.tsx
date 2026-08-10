import { useRef, useState } from 'react';

const FILES = [
    {
        title: 'orbital-checkout / App.tsx',
        lines: [
            { n: 1, node: <><span className="cm">// live session — 3 collaborators</span></> },
            { n: 2, node: <><span className="kw">import</span> {'{'} useSession {'}'} <span className="kw">from</span> <span className="str">'@xenosync/session'</span>;</> },
            { n: 3, node: <></> },
            { n: 4, node: <><span className="kw">export function</span> <span className="fn">CheckoutForm</span>() {'{'}</> },
            { n: 5, node: <>&nbsp;&nbsp;<span className="kw">const</span> [total, setTotal] = <span className="fn">useState</span>(0);</> },
            { n: 6, node: <>&nbsp;&nbsp;<span className="kw">const</span> {'{'} cart {'}'} = <span className="fn">useSession</span>(<span className="str">'orbital-checkout'</span>);</> },
            { n: 7, node: <></> },
            { n: 8, node: <>&nbsp;&nbsp;<span className="kw">return</span> <span className="tg">&lt;Total</span> <span className="fn">amount</span>={'{total}'} <span className="tg">/&gt;</span>;</> },
            { n: 9, node: <>{'}'}</> },
        ],
    },
    {
        title: 'orbital-checkout / routes.py',
        lines: [
            { n: 1, node: <><span className="kw">from</span> flask <span className="kw">import</span> Blueprint, jsonify</> },
            { n: 2, node: <></> },
            { n: 3, node: <>checkout = <span className="fn">Blueprint</span>(<span className="str">'checkout'</span>, __name__)</> },
            { n: 4, node: <></> },
            { n: 5, node: <><span className="tg">@checkout.route</span>(<span className="str">'/cart'</span>)</> },
            { n: 6, node: <><span className="kw">def</span> <span className="fn">get_cart</span>():</> },
            { n: 7, node: <>&nbsp;&nbsp;<span className="kw">return</span> <span className="fn">jsonify</span>(session.cart)</> },
            { n: 8, node: <></> },
            { n: 9, node: <><span className="cm"># synced with App.tsx in real time</span></> },
        ],
    },
    {
        title: 'orbital-checkout / Main.java',
        lines: [
            { n: 1, node: <><span className="kw">package</span> com.xenosync.checkout;</> },
            { n: 2, node: <></> },
            { n: 3, node: <><span className="tg">@SpringBootApplication</span></> },
            { n: 4, node: <><span className="kw">public class</span> <span className="fn">Main</span> {'{'}</> },
            { n: 5, node: <>&nbsp;&nbsp;<span className="kw">public static void</span> <span className="fn">main</span>(String[] args) {'{'}</> },
            { n: 6, node: <>&nbsp;&nbsp;&nbsp;&nbsp;SpringApplication.<span className="fn">run</span>(Main.class, args);</> },
            { n: 7, node: <>&nbsp;&nbsp;{'}'}</> },
            { n: 8, node: <>{'}'}</> },
        ],
    },
];

const LOG_LINES = [
    { text: '$ mvn spring-boot:run', cls: 'info' },
    { text: '[INFO] Building orbital-checkout-api 1.4.0', cls: 'info' },
    { text: 'Tomcat started on port 8080 — ready in 1.6s', cls: 'ok' },
    { text: '✓ Connected to schema.sql — 14 tables loaded', cls: 'ok' },
];

export default function HeroMock() {
    const [activeFile, setActiveFile] = useState(0);
    const [consoleLines, setConsoleLines] = useState<{ text: string; cls: string }[]>([]);
    const [consoleOpen, setConsoleOpen] = useState(false);
    const runningRef = useRef(false);
    const codeAreaRef = useRef<HTMLDivElement>(null);
    const [youPos, setYouPos] = useState<{ x: number; y: number; visible: boolean }>({
        x: 0,
        y: 0,
        visible: false,
    });

    function runDemo() {
        if (runningRef.current) return;
        runningRef.current = true;
        setConsoleOpen(true);
        setConsoleLines([]);

        let li = 0;
        function nextLine() {
            if (li >= LOG_LINES.length) {
                runningRef.current = false;
                return;
            }
            const line = LOG_LINES[li];
            let ci = 0;
            setConsoleLines((prev) => [...prev, { text: '', cls: line.cls }]);
            const t = setInterval(() => {
                ci++;
                setConsoleLines((prev) => {
                    const copy = [...prev];
                    copy[copy.length - 1] = { text: line.text.slice(0, ci), cls: line.cls };
                    return copy;
                });
                if (ci >= line.text.length) {
                    clearInterval(t);
                    li++;
                    setTimeout(nextLine, 220);
                }
            }, 14);
        }
        nextLine();
    }

    function handleMouseMove(e: React.MouseEvent) {
        const rect = codeAreaRef.current?.getBoundingClientRect();
        if (!rect) return;
        setYouPos({ x: e.clientX - rect.left, y: e.clientY - rect.top, visible: true });
    }

    return (
        <div className="hero-mock-wrap">
            <div className="hero-mock">
                <div className="mock-bar">
                    <span className="mock-dot r" /><span className="mock-dot y" /><span className="mock-dot g" />
                    <span className="mock-title">{FILES[activeFile].title}</span>
                    <button className="mock-run" onClick={runDemo}>▶ Run</button>
                </div>

                <div className="mock-body">
                    <div className="mock-side">
                        {FILES.map((f, i) => (
                            <div
                                key={i}
                                className={`f${i === activeFile ? ' active' : ''}`}
                                onClick={() => setActiveFile(i)}
                            >
                                ◆ {f.title.split(' / ')[1]}
                            </div>
                        ))}
                    </div>

                    <div
                        className="mock-code-area"
                        ref={codeAreaRef}
                        onMouseMove={handleMouseMove}
                        onMouseLeave={() => setYouPos((p) => ({ ...p, visible: false }))}
                    >
                        <div className="mock-code">
                            {FILES[activeFile].lines.map((l) => (
                                <div className="ln" key={l.n}>
                                    <span className="n">{l.n}</span>{l.node}
                                </div>
                            ))}
                        </div>

                        <div className="ghost-cursor gc1" style={{ color: 'var(--user-k)' }}>
                            <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
                                <path d="M1 1l5.5 13 2-5.5L14 6.5z" />
                            </svg>
                            <span className="gc-label" style={{ background: 'var(--user-k)', color: '#0c0820' }}>Kost</span>
                        </div>
                        <div className="ghost-cursor gc2" style={{ color: 'var(--user-r)' }}>
                            <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
                                <path d="M1 1l5.5 13 2-5.5L14 6.5z" />
                            </svg>
                            <span className="gc-label" style={{ background: 'var(--user-r)', color: '#2b0f04' }}>Reo</span>
                        </div>
                        <div
                            className="you-cursor"
                            style={{ left: youPos.x, top: youPos.y, opacity: youPos.visible ? 1 : 0 }}
                        />

                        <div className={`mock-console${consoleOpen ? ' open' : ''}`}>
                            {consoleLines.map((l, i) => (
                                <div key={i} className={l.cls}>{l.text}</div>
                            ))}
                        </div>
                    </div>

                    <div className="mock-right">
                        <div className="rtitle">Session chat</div>
                        <div className="mock-msg">
                            <div className="mock-av k">K</div>
                            <div>
                                <div className="mock-msg-name" style={{ color: 'var(--user-k)' }}>Kost</div>
                                <div className="mock-msg-text">pushed the promo bean, re-run schema?</div>
                            </div>
                        </div>
                        <div className="mock-msg">
                            <div className="mock-av m">M</div>
                            <div>
                                <div className="mock-msg-name" style={{ color: 'var(--user-m)' }}>Mira</div>
                                <div className="mock-msg-text">on it — spinning up mvn now</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}