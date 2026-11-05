from pathlib import Path
import re,json,subprocess
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
root=Path('/tmp/uxr-hero-fix-qa');out=root/'upgrade';data={}
for phase in ('before','after'):
 pending=None;rows=[]
 for event,wide,at in re.findall(r'(decode|commit) wide=(true|false|null) at=(\d+)',(out/f'{phase}-timing.log').read_text()):
  if wide!='true':continue
  if event=='decode':pending=int(at)
  elif pending is not None:
   rows.append({'decode_ready_ms':pending,'commit_ms':int(at),'lag_ms':int(at)-pending});pending=None
 data[phase]={'cached_trials':rows[-4:],'all_trials':rows}
for phase in ('before','after','production'):
 path=root/'motion'/f'upgrade-{phase}-top.mp4';stats=out/f'{phase}-diff.txt'
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(path),'-vf',f'tblend=all_mode=difference,signalstats,metadata=print:file={stats}','-fps_mode','passthrough','-enc_time_base','demux','-an','-f','null','-'],check=True)
 values=[float(v) for v in re.findall(r'lavfi.signalstats.YAVG=([0-9.]+)',stats.read_text())]
 data.setdefault(phase,{})['frame_diff']={'frames':len(values),'max_yavg':max(values,default=0),'above20':sum(v>20 for v in values)}
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(path),'-vf','fps=12,scale=480:-1,tile=6x4','-frames:v','1','-y',str(out/f'{phase}-sheet.png')],check=True)
fig,ax=plt.subplots(figsize=(8,4.5),layout='constrained')
for phase,label,color,offset in [('before','Before','#b86b24',-.12),('after','After','#147b71',.12)]:
 vals=[r['lag_ms'] for r in data[phase]['cached_trials']];x=[i+1+offset for i in range(len(vals))]
 ax.scatter(x,vals,label=label,color=color,s=65)
 ax.plot(x,vals,color=color,alpha=.4)
 for xp,y in zip(x,vals):ax.annotate(f'{y} ms',(xp,y),xytext=(0,9),textcoords='offset points',ha='center',fontsize=10,color=color)
ax.axhline(180,color='#888888',linestyle='--',linewidth=1,label='Scene dwell: 180 ms')
ax.set(title='Thor: decoded wide art commits without another dwell',xlabel='Cached cover → wide/logo trial',ylabel='Decode-ready → commit (ms)',xticks=[1,2,3,4],ylim=(-10,215))
ax.spines[['top','right']].set_visible(False);ax.grid(alpha=.15);ax.legend(loc='center right')
fig.savefig(out/'timing-chart.png',dpi=160)
(out/'timing-metrics.json').write_text(json.dumps(data,indent=2));print(json.dumps(data,indent=2))
