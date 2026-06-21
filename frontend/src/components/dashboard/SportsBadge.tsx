// frontend/src/components/dashboard/SportBadge.tsx
import { SportType } from '@/types/event';

interface SportBadgeProps {
    sportType: SportType;
}

export default function SportBadge({ sportType }: SportBadgeProps) {
    // We'll give each sport its own custom color theme!
    const getBadgeStyles = () => {
        switch (sportType) {
            case 'SOCCER':
                return 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30';
            case 'BASKETBALL':
                return 'bg-orange-500/20 text-orange-400 border-orange-500/30';
            case 'FOOTBALL':
                return 'bg-amber-500/20 text-amber-400 border-amber-500/30';
            case 'BASEBALL':
                return 'bg-blue-500/20 text-blue-400 border-blue-500/30';
            case 'HOCKEY':
                return 'bg-cyan-500/20 text-cyan-400 border-cyan-500/30';
            case 'FORMULA_1':
                return 'bg-rose-500/20 text-rose-400 border-rose-500/30';
            default:
                return 'bg-indigo-500/20 text-indigo-400 border-indigo-500/30';
        }
    };

    return (
        <span className={`px-3 py-1 text-[10px] font-black tracking-widest rounded-full border uppercase shadow-sm ${getBadgeStyles()}`}>
            {sportType.replace('_', ' ')}
        </span>
    );
}
