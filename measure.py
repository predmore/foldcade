from pathlib import Path
import subprocess,re,json
root=Path('/tmp/uxr-hero-fix-qa/motion');results=[]
for path in root.glob('*-top.mp4'):
 if path.stem not in ('before-slow-top','after-slow-top','after-normal-top','after-no-art-top'):continue
 stats=path.with_suffix('.diff.txt')
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(path),'-vf',f'tblend=all_mode=difference,signalstats,metadata=print:file={stats}','-an','-f','null','-'],check=True)
 values=[float(x) for x in re.findall(r'lavfi.signalstats.YAVG=([0-9.]+)',stats.read_text())]
 duration=float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=nw=1:nk=1',str(path)]))
 sheet=path.with_suffix('.png')
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(path),'-vf','fps=3,scale=480:-1,tile=6x4','-frames:v','1','-y',str(sheet)],check=True)
 results.append(dict(video=path.name,frames=len(values),duration=duration,max_yavg=max(values,default=0),above20=sum(v>20 for v in values),peaks=sorted(enumerate(values),key=lambda x:x[1],reverse=True)[:5]))
(root/'metrics.json').write_text(json.dumps(results,indent=2));print(json.dumps(results,indent=2))
