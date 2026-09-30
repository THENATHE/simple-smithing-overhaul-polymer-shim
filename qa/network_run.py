#!/usr/bin/env python3
"""Official vanilla connection plus fixture-only client network menu transactions."""
from pathlib import Path
import hashlib,importlib.util,json,os,shutil,subprocess,time,zipfile
QA=Path(__file__).resolve().parent
PROJECT=QA.parent
spec=importlib.util.spec_from_file_location('baseline',PROJECT.parent/'mapstitch-polymer-compat-26.3/qa/launch.py')
baseline=importlib.util.module_from_spec(spec);spec.loader.exec_module(baseline)
RUN=QA/'network-run'
CONTROL=RUN/'control'
PORT=25684

def build():
    classes=QA/'network-classes';classes.mkdir(exist_ok=True)
    server,client=baseline.audits()
    paths=baseline.cp(server['command'])+baseline.cp(client['command'])
    for jar in (PROJECT/'libs').glob('*.jar'):
        paths.append(str(jar))
        with zipfile.ZipFile(jar) as z:
            for member in z.namelist():
                if member.endswith('.jar'):
                    dest=QA/'network-nested'/Path(member).name;dest.parent.mkdir(exist_ok=True);dest.write_bytes(z.read(member));paths.append(str(dest))
    result=subprocess.run(['javac','--release','25','-proc:none','-cp',os.pathsep.join(dict.fromkeys(paths)),'-d',str(classes),*map(str,(QA/'network').glob('*.java'))])
    if result.returncode:raise SystemExit(result.returncode)
    for side in ['server','client']:
        path=QA/f'network-{side}-qa.jar'
        with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
            z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'sso_network_'+side+'_qa','version':'1','environment':side,'entrypoints':{'main' if side=='server' else 'client':['qa.Network'+side.title()+'Qa']},'depends':{'fabric-api':'*'}}))
            for p in classes.rglob('Network'+side.title()+'Qa*.class'):z.write(p,p.relative_to(classes))

def prepare(mode):
    run=RUN/mode;run.mkdir(parents=True,exist_ok=True);mods=run/'mods';mods.mkdir(exist_ok=True)
    for p in mods.glob('*.jar'):p.unlink()
    selected=[]
    if mode=='server':
        selected=list((PROJECT/'libs').glob('*.jar'))+[baseline.polymer(),PROJECT/'build/libs/simple-smithing-polymer-compat-1.0.0+26.3.jar',QA/'network-server-qa.jar']
        source=PROJECT.parent/'mapstitch-polymer-compat-26.3/qa/runs/server/eula.txt'
        if 'eula=true' not in source.read_text().splitlines():raise RuntimeError('Accepted existing QA EULA unavailable')
        shutil.copy2(source,run/'eula.txt')
        (run/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\ngamemode=survival\ndifficulty=peaceful\npause-when-empty-seconds=0\n')
    elif mode=='native':
        selected=[PROJECT/'libs/fabric-api-0.161.0+26.3.jar',QA/'network-client-qa.jar']
    for source in selected:shutil.copy2(source,mods/source.name)
    if mode!='server':
        (run/'options.txt').write_text('graphicsMode:0\nrenderDistance:2\nsimulationDistance:5\nmaxFps:20\nmaxFpsInactive:20\nsoundCategory_master:0.0\njoinedFirstServer:true\n')
    if mode=='native':
        packs=run/'resourcepacks';packs.mkdir(exist_ok=True)
        shutil.copy2(RUN/'server/polymer/resource_pack.zip',packs/'sso-qa.zip')
        with (run/'options.txt').open('a') as options:options.write('resourcePacks:["vanilla","file/sso-qa.zip"]\n')
    command=baseline.base_command(mode,run,PORT)
    command.insert(1,'-Dsso.network.control='+str(CONTROL))
    (run/'launch-audit.json').write_text(json.dumps({'mode':mode,'command':command,'mods':[{'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}for p in sorted(mods.glob('*.jar'))],'content_mods_on_client':[] if mode!='server' else None},indent=2))
    return run,command

def wait(condition,processes,timeout=150):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        for file in ['client-failure','server-failure']:
            if(CONTROL/file).exists():raise RuntimeError((CONTROL/file).read_text())
        if condition():return
        if any(p.poll()is not None for p in processes):raise RuntimeError('QA process exited early')
        time.sleep(.25)
    raise RuntimeError('QA phase timed out')

def main():
    build();CONTROL.mkdir(parents=True,exist_ok=True)
    for p in CONTROL.iterdir():
        if p.is_file():p.unlink()
    active=[];logs=[];result={'passed':False};server=None
    env=os.environ.copy();env.update(SDL_VIDEODRIVER='x11',SDL_VIDEO_X11_XINPUT2='0',LP_NUM_THREADS='4')
    def launch(mode):
        run,command=prepare(mode);log=(run/'console.log').open('w');logs.append(log)
        process=subprocess.Popen(command,cwd=run,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,env=env,text=True);active.append(process)
        return process,run
    try:
        server,srun=launch('server')
        wait(lambda:'Done ('in(srun/'console.log').read_text(errors='replace'),[server])
        print('Server ready',flush=True)
        server.stdin.write('polymer generate-pack\n');server.stdin.flush()
        vanilla,vrun=launch('vanilla')
        wait(lambda:(CONTROL/'vanilla-joined').exists(),[server,vanilla])
        result['official_vanilla_join']=True;print('Official unmodified vanilla join passed',flush=True)
        vanilla.terminate();vanilla.wait(timeout=20);active.remove(vanilla)
        client,crun=launch('native')
        wait(lambda:(CONTROL/'network-result.txt').exists(),[server,client],timeout=240)
        result['menu_network_transactions']=True;result['passed']=True
        print((CONTROL/'network-result.txt').read_text(),flush=True)
    except Exception as error:
        result['failure']=str(error);print(result,flush=True)
    finally:
        for process in reversed(active):
            if process.poll()is None:
                if process is server:
                    try:process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=25)
                    except Exception:process.terminate()
                else:process.terminate()
                try:process.wait(timeout=20)
                except subprocess.TimeoutExpired:process.kill();process.wait()
        for log in logs:log.close()
        pack=RUN/'server/polymer/resource_pack.zip'
        if pack.is_file():
            with zipfile.ZipFile(pack)as archive:result['resource_pack_integrity']=archive.testzip()is None
            result['resource_pack_sha256']=hashlib.sha256(pack.read_bytes()).hexdigest()
        clientlog=RUN/'native/console.log'
        if clientlog.exists():result['resource_pack_loaded']='fabricloader, file/sso-qa.zip'in clientlog.read_text(errors='replace')
        (RUN/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    return 0 if result['passed']else 1

if __name__=='__main__':raise SystemExit(main())
