import React, { useState } from 'react';
import { Settings, FolderOpen, RefreshCw, Gamepad2, ChevronDown, Edit3 } from 'lucide-react';
import { motion } from 'framer-motion';
import BackgroundImage from './components/BackgroundImage';
import Titlebar from './components/Titlebar';
import { DownloadGame } from '../wailsjs/go/main/App';

const App: React.FC = () => {
  const [downloadProgress] = useState(42);

  const handlePlay = async () => {
    try {
      await DownloadGame();
      console.log("Download started");
    } catch (err) {
      console.error(err);
    }
  };


  return (
    <div className="relative w-[1280px] h-[720px] bg-[#090909] text-white overflow-hidden font-sans select-none shadow-2xl rounded-[14px] border border-white/5">
      <BackgroundImage />
      <Titlebar />

      <main className="relative z-10 h-full p-10 flex flex-col justify-between pt-[60px]">
        
        {/* Top Section */}
        <div className="flex justify-between items-start">
          <div className="flex flex-col gap-4">
            {/* Profile Block (294x100) */}
            <div className="w-[294px] h-[100px] bg-[#090909]/[0.55] backdrop-blur-xl rounded-[14px] border border-[#FFA845]/[0.10] p-4 flex flex-col justify-center gap-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-gray-200">HyLauncher-Player001</span>
                <Edit3 size={14} className="text-gray-400 cursor-pointer hover:text-white transition-colors" />
              </div>
              <div className="flex items-center justify-between bg-[#090909]/[0.55] backdrop-blur-md rounded-lg px-3 py-2 border border-white/5 cursor-pointer hover:bg-white/5 transition-colors">
                <span className="text-xs text-gray-300">Latest Version 1.0</span>
                <ChevronDown size={14} className="text-gray-400" />
              </div>
            </div>
          </div>

          {/* News Feed */}
          <div className="flex flex-col gap-4">
            {[1, 2, 3].map((i) => (
              <motion.div 
                key={i}
                whileHover={{ x: -5, borderColor: 'rgba(255, 168, 69, 0.2)' }}
                className="w-[532px] h-[120px] bg-[#090909]/[0.55] backdrop-blur-xl rounded-[14px] border border-[#FFA845]/[0.10] p-4 flex gap-4 cursor-pointer"
              >
                <div className="flex-1">
                  <h3 className="text-sm font-bold text-gray-200 leading-snug">
                    Latest News: The update is almost here...
                  </h3>
                </div>
                <div className="w-[160px] h-full bg-[#090909]/[0.55] backdrop-blur-md rounded-lg border border-[#FFA845]/[0.10] flex items-center justify-center overflow-hidden">
                   <div className="text-[10px] text-[#FFA845]/[0.30] font-black uppercase tracking-widest">Hytale</div>
                </div>
              </motion.div>
            ))}
          </div>
        </div>

        {/* Bottom Section */}
        <div className="w-full">
          <div className="flex items-end gap-8">
            
            {/* Left Column: Fixed 294px width for perfect vertical alignment */}
            <div className="w-[294px] flex flex-col gap-3">
              {/* Utility Buttons Row (4x66px + 3x10px gap = 294px) */}
              <div className="flex gap-[10px]">
                <NavButton icon={<Gamepad2 size={20}/>} />
                <NavButton icon={<FolderOpen size={20}/>} />
                <NavButton icon={<RefreshCw size={20}/>} />
                <NavButton icon={<Settings size={20}/>} />
              </div>

              {/* Play Button */}
              <motion.button 
                whileHover={{ scale: 1.01, backgroundColor: 'rgba(9, 9, 9, 0.7)', borderColor: 'rgba(255, 168, 69, 0.4)' }}
                whileTap={{ scale: 0.99 }}
                className="w-[294px] h-[94px] bg-[#090909]/[0.55] backdrop-blur-xl text-white font-black text-4xl tracking-tighter rounded-[14px] border border-[#FFA845]/[0.10] shadow-lg transition-all cursor-pointer"
                onClick={handlePlay}
              >
                PLAY
              </motion.button>
            </div>

            {/* Right Column: Progress Area */}
            <div className="flex-1 flex flex-col gap-4 pb-1">
              <div className="flex justify-between items-end">
                <div className="flex items-baseline gap-4">
                  <span className="text-5xl font-bold italic tracking-tighter">{downloadProgress}%</span>
                  <span className="text-[11px] text-gray-400 uppercase font-bold tracking-widest opacity-70">
                    Downloading: lwgl-opengl-natives.jar
                  </span>
                </div>
                <div className="text-[11px] text-gray-400 font-mono">
                  42.5 MB/s • 6742.52 MB / 10024.5 MB
                </div>
              </div>
              <div className="h-2 w-full bg-white/5 rounded-full overflow-hidden border border-white/5">
                <motion.div 
                  initial={{ width: 0 }}
                  animate={{ width: `${downloadProgress}%` }}
                  className="h-full bg-white progress-glow"
                />
              </div>
            </div>

          </div>
        </div>
      </main>
    </div>
  );
};

// Fixed width to 66px for mathematical alignment
const NavButton = ({ icon }: { icon: React.ReactNode }) => (
  <button className="w-[66px] h-[42px] flex items-center justify-center bg-[#090909]/[0.55] backdrop-blur-xl border border-[#FFA845]/[0.10] rounded-[14px] hover:bg-[#FFA845]/[0.05] hover:border-[#FFA845]/[0.30] transition-all cursor-pointer text-gray-400 hover:text-white">
    {icon}
  </button>
);

export default App;