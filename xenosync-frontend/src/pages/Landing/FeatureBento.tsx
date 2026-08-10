export default function FeatureBento() {
    return (
        <section className="section" id="features">
            <div className="wrap">
                <div className="section-head">
                    <div className="section-label">// why teams switch</div>
                    <h2>One tab. Every piece of the loop.</h2>
                    <p>
                        Stop shuttling between an editor, a terminal, a database client, and a chat app.
                        XenoSync keeps the whole session — and everyone in it — in sync.
                    </p>
                </div>

                <div className="bento">
                    <div className="bcard c1">
                        <span className="pulse-badge" />
                        <div className="feature-icon fi-1">👥</div>
                        <h3>Real-time editing</h3>
                        <p>See every teammate's cursor and edits land instantly, with presence indicators and zero merge-conflict surprises.</p>
                    </div>

                    <div className="bcard c2">
                        <div className="feature-icon fi-2">💬</div>
                        <h3>Session chat</h3>
                        <p>Discuss the change next to the change. Chat is scoped to the session, so context never gets lost in another app.</p>
                    </div>

                    <div className="bcard c3">
                        <div className="feature-icon fi-3">⑂</div>
                        <h3>GitHub, built in</h3>
                        <p>Review the diff, write the commit, and push — without leaving the session or opening a separate GitHub tab.</p>
                    </div>

                    <div className="bcard c4" id="run">
                        <h3>Run anything, right where you write it</h3>
                        <p>Spin up a full runtime per session — Spring Boot, .NET, Node, or Python — and test your change without a local environment.</p>
                        <div className="stack-tags">
                            <span>Java · Spring Boot</span><span>C# · .NET</span><span>Node.js</span><span>Python</span><span>Go</span>
                        </div>
                        <div className="console-preview">
                            <div><span className="info">$</span> mvn spring-boot:run</div>
                            <div className="info">[INFO] Building orbital-checkout-api 1.4.0</div>
                            <div className="ok">Tomcat started on port 8080 — ready in 1.6s</div>
                        </div>
                    </div>

                    <div className="bcard c5" id="collab">
                        <h3>A real database, in the same tab</h3>
                        <p>Query, seed, and inspect data live — the same schema your run session already connects to.</p>
                        <div className="db-preview">
                            <table>
                                <tbody>
                                <tr><th>sku</th><th>price</th><th>status</th></tr>
                                <tr><td>ORB-2201</td><td>48.00</td><td>active</td></tr>
                                <tr><td>ORB-3387</td><td>62.00</td><td>pending</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}