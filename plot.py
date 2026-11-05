from pathlib import Path
import re,json
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
root=Path('/tmp/uxr-hero-fix-qa/motion')
def points(path):
 return [(float(t),float(y)) for t,y in re.findall(r'frame:\d+\s+pts:[-\d]+\s+pts_time:([0-9.]+).*?lavfi.signalstats.YAVG=([0-9.]+)',path.read_text(),re.S)]
fig,axes=plt.subplots(2,1,figsize=(11,7),sharex=True,layout='constrained')
summary={}
for phase,label,color in [('before','Before (merged #375)','#b86b24'),('after','After','#147b71')]:
 art=points(root/f'{phase}-ambient.txt')
 diff=points(root/f'{phase}-slow-top.diff.txt')
 axes[0].plot(*zip(*art),label=label,color=color,linewidth=2)
 axes[1].plot(*zip(*diff),label=label,color=color,linewidth=1.6)
 low=[(t,y) for t,y in art if y<25]
 summary[phase]={'roi':'crop=200:400:150:300','minimum_luma':min(y for _,y in art),'below25_frames':len(low),'below25_start':low[0][0] if low else None,'below25_end':low[-1][0] if low else None,'gap_seconds':low[-1][0]-low[0][0] if low else 0}
axes[0].axhline(25,color='#777777',linestyle='--',linewidth=1)
axes[0].axvspan(summary['before']['below25_start'],summary['before']['below25_end'],color='#b86b24',alpha=.12)
axes[0].set_title('Artwork background stays present through the decode wait',loc='left',fontweight='bold')
axes[0].set_ylabel('Background ROI luma')
axes[0].text(1.75,27,'Before: 1.77 s with theme / platform placeholder',fontsize=9,color='#98501d')
axes[0].legend(loc='upper right')
axes[1].set_title('Native-frame image differences remain small in both runs',loc='left',fontweight='bold')
axes[1].set_ylabel('Full-panel difference (YAVG)')
axes[1].set_xlabel('Recording time (seconds)')
for ax in axes:
 ax.set_xlim(0,5)
 ax.grid(alpha=.15)
 ax.spines[['top','right']].set_visible(False)
fig.suptitle('Thor: Sonic → Bravely Second → Y, identical 2.5-second decode fixture',fontsize=13,fontweight='bold')
fig.savefig(root/'readiness-chart.png',dpi=160)
(root/'ambient-metrics.json').write_text(json.dumps(summary,indent=2))
print(json.dumps(summary,indent=2))
