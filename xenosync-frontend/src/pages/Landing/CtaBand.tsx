export default function CtaBand({ onSignup }: { onSignup: () => void }) {
    return (
        <div className="cta-band">
            <h2>Your next session starts in seconds.</h2>
            <p>No local setup. No environment drift. Just a link your team can join.</p>
            <a className="btn btn-primary btn-lg" onClick={onSignup}>Create a free session</a>
        </div>
    );
}