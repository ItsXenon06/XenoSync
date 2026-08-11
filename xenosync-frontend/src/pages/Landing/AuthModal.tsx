import PlanetLogo from '../../components/Logo/PlanetLogo';

type Mode = 'login' | 'signup';

interface Props {
    open: boolean;
    mode: Mode;
    onClose: () => void;
    onSwitchMode: (mode: Mode) => void;
}

export default function AuthModal({ open, mode, onClose, onSwitchMode }: Props) {
    function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        // TODO: wire to real backend — POST /auth/login or /auth/signup
        console.log(`submit ${mode}`);
    }

    return (
        <div
            className={`modal-overlay${open ? ' open' : ''}`}
            onClick={(e) => {
                if (e.target === e.currentTarget) onClose();
            }}
        >
            <div className="auth-card" data-mode={mode}>
                <button className="auth-close" onClick={onClose}>✕</button>

                <div className="auth-head">
                    <PlanetLogo size={70} className="auth-mark" />
                    {mode === 'login' ? (
                        <>
                            <h2>Welcome back</h2>
                            <p>Log in to rejoin your team's sessions.</p>
                        </>
                    ) : (
                        <>
                            <h2>Create your session</h2>
                            <p>Free for up to 3 collaborators per session.</p>
                        </>
                    )}
                </div>

                <div className="auth-tabs">
                    <div
                        className="auth-tab-slider"
                        style={{ transform: mode === 'signup' ? 'translateX(100%)' : 'translateX(0)' }}
                    />
                    <div
                        className={`auth-tab${mode === 'login' ? ' active' : ''}`}
                        onClick={() => onSwitchMode('login')}
                    >
                        Log in
                    </div>
                    <div
                        className={`auth-tab${mode === 'signup' ? ' active' : ''}`}
                        onClick={() => onSwitchMode('signup')}
                    >
                        Sign up
                    </div>
                </div>

                <form className="auth-body" onSubmit={handleSubmit}>
                    <div className="oauth-row">
                        <div className="oauth-btn">⌾ GitHub</div>
                        <div className="oauth-btn">◐ Google</div>
                    </div>
                    <div className="auth-divider">or with email</div>

                    {mode === 'signup' && (
                        <div className="field">
                            <label>Full name</label>
                            <input type="text" placeholder="Mira Alston" />
                        </div>
                    )}

                    <div className="field">
                        <label>Email</label>
                        <input type="email" placeholder="you@company.com" />
                    </div>

                    <div className="field">
                        <label>Password</label>
                        <input type="password" placeholder="••••••••••" />
                    </div>

                    {mode === 'login' && (
                        <div className="field-row">
                            <label className="remember">
                                <input type="checkbox" style={{ width: 'auto' }} /> Remember me
                            </label>
                            <a href="#">Forgot password?</a>
                        </div>
                    )}

                    <button className="auth-submit" type="submit">
                        {mode === 'login' ? 'Log in' : 'Create free account'}
                    </button>

                    <div className="auth-foot">
                        {mode === 'login' ? (
                            <>Don't have an account? <a onClick={() => onSwitchMode('signup')}>Sign up</a></>
                        ) : (
                            <>Already have an account? <a onClick={() => onSwitchMode('login')}>Log in</a></>
                        )}
                    </div>
                </form>
            </div>
        </div>
    );
}