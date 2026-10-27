import { useEffect, useState } from 'react'

const messages = [
  'opening the bifrost',
  'awakening the guardian',
  'connecting to the jvm',
  'reading the nine realms',
  'collecting metrics',
  'initializing heimdall',
]

export function BifrostLoader() {
  const [msgIndex, setMsgIndex] = useState(0)

  useEffect(() => {
    const interval = setInterval(() => {
      setMsgIndex(i => (i + 1) % messages.length)
    }, 2000)
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="min-h-screen bg-gray-950 flex flex-col items-center justify-center gap-10">



      <div style={{ position: 'relative', width: '380px', height: '380px' }}>
        <video
          src={`${import.meta.env.BASE_URL}bifrost.mp4`}
          autoPlay
          loop
          muted
          playsInline
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'contain',
            mixBlendMode: 'screen',
            maskImage: `radial-gradient(ellipse 45% 80% at 50% 50%, 
              black 0%, 
              black 30%, 
              transparent 70%
            )`,
            WebkitMaskImage: `radial-gradient(ellipse 45% 60% at 50% 50%, 
              black 0%, 
              black 30%, 
              transparent 70%
            )`,
          }}
        />
      </div>

      {/* text */}
      <div className="flex flex-col items-center gap-3">
        <h1
          className="font-mono text-2xl text-white tracking-widest"
          style={{ animation: 'title-glow 2s ease-in-out infinite' }}
        >
          HEIMDALL
        </h1>

        <p
          className="font-mono text-xs tracking-widest text-green-400 uppercase"
          style={{ animation: 'status-fade 2s ease-in-out infinite' }}
        >
          {messages[msgIndex]}
        </p>

        <div className="flex gap-1.5 mt-1">
          {[0, 1, 2].map(i => (
            <div
              key={i}
              className="w-1 h-1 rounded-full"
              style={{
                background: i === 0 ? '#00e5a0' : i === 1 ? '#3b82f6' : '#a78bfa',
                animation: `dot-bounce 1.2s ease-in-out ${i * 0.2}s infinite`
              }}
            />
          ))}
        </div>
      </div>

      {/* keyframes */}
      <style>{`
        @keyframes spin {
          from { transform: translate(-50%, -50%) rotate(0deg); }
          to   { transform: translate(-50%, -50%) rotate(360deg); }
        }
        @keyframes spin-reverse {
          from { transform: translate(-50%, -50%) rotate(0deg); }
          to   { transform: translate(-50%, -50%) rotate(-360deg); }
        }
        @keyframes beam-appear {
          0%,100% { opacity: 0; }
          30%      { opacity: 0.8; }
          60%      { opacity: 0.3; }
        }
        @keyframes core-pulse {
          0%,100% { box-shadow: 0 0 6px #00e5a0, 0 0 12px #00e5a0; transform: translate(-50%, -50%) scale(1); }
          50%      { box-shadow: 0 0 12px #00e5a0, 0 0 30px #00e5a0, 0 0 50px rgba(0,229,160,0.3); transform: translate(-50%, -50%) scale(1.2); }
        }
        @keyframes title-glow {
          0%,100% { text-shadow: 0 0 10px rgba(0,229,160,0.3); }
          50%      { text-shadow: 0 0 20px rgba(0,229,160,0.7), 0 0 40px rgba(0,229,160,0.3); }
        }
        @keyframes status-fade {
          0%,100% { opacity: 0.4; }
          50%      { opacity: 1; }
        }
        @keyframes dot-bounce {
          0%,100% { opacity: 0.2; transform: scale(0.8); }
          50%      { opacity: 1;   transform: scale(1.2); }
        }
      `}</style>

    </div>
  )
}