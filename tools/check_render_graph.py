#!/usr/bin/env python3
"""Source-contract checks only. These are NOT frame captures, Compose builds or device tests."""
from pathlib import Path
import re,json
R=Path(__file__).resolve().parents[1]
U=R/'app/src/main/java/com/luma/downloader/ui'
checks=[]
def check(name,value):
    checks.append(dict(name=name,passed=bool(value)))
    if not value:raise AssertionError(name)
surface=(U/'GlassSurface.kt').read_text()
root=(U/'ShiliuApp.kt').read_text()
menu=(U/'SpringDropdownMenu.kt').read_text()
host=(U/'GlassOverlayHost.kt').read_text()
settings=(U/'SettingsScreen.kt').read_text()
parts=(U/'UiParts.kt').read_text()
controls=(U/'LiquidControls.kt').read_text()
dialog=(U/'GlassAlertDialog.kt').read_text()
check('only the two persistent root nodes capture UI backgrounds',sum(p.read_text().count('.hazeSource(') for p in U.glob('*.kt'))==2 and root.count('.hazeSource(')==2)
check('per-card source chains removed','captureSurface' not in surface and 'rememberHazeState(' not in parts and 'rememberHazeState(' not in controls)
check('source attachment readiness does not swap composable material branches','.areas' not in surface and 'sourceAvailable=source!=null' in surface)
check('Haze effect remains mounted for a stable source','if(source!=null)' in surface and 'blurEnabled=plan.enabled' in surface)
check('menus do not create Android Popup windows','androidx.compose.ui.window.Popup' not in menu and not re.search(r'\bPopup\s*\(',menu))
check('dialogs do not create Android Dialog windows','androidx.compose.ui.window.Dialog' not in dialog and not re.search(r'\bDialog\s*\(',dialog))
check('overlay captured composition locals remain current','rememberUpdatedState(currentCompositionLocalContext)' in menu and 'CompositionLocalProvider(entry.locals())' in host)
check('menu geometry is computed directly in one measure pass','SubcomposeLayout' in host and 'PopupPositionProvider' not in menu and 'MenuPolicy.place(' in host)
check('opacity is independent of spring overshoot','alpha=entry.opacity.value' in host and 'alpha=frame.alpha' not in host and 'LinearEasing' in host)
check('theme/material selection runs after menu fade finishes',host.index('opacity.animateTo(0f') < host.index('action?.invoke()') and 'select(onClick)' in menu)
check('retained settings home exists below conditional detail',settings.index('holder.SaveableStateProvider("home")') < settings.index('if(detailVisible)'))
check('predictive cancellation uses an animation rather than an immediate reset','NonCancellable' in settings and 'predictive.animateTo(0f' in settings)
check('old fixed transition timeout removed','delay(800)' not in settings)
check('only active page and section publish navigation scroll state','active==section' in settings and 'vm.page==owner' in parts)
check('row ripple is not captured as a bright patch during navigation','indication=null' in parts and 'indication=null' in root)
check('private dialogs still request screenshot protection','FLAG_SECURE' in host and 'secure=true' in (U/'AccountScreen.kt').read_text())
check('same-window overlay owns back and outside click','BackHandler {entry.close()}' in host and 'clickable(interactionSource=interactions,indication=null){entry.close()}' in host)
check('inactive page remains visually present but cannot accept clicks','suppressPaneInput(detailVisible)' in settings and 'suppressPaneInput(route=="home")' in settings)
out=R/'tests/fix-004';out.mkdir(parents=True,exist_ok=True)
(out/'render-contract.json').write_text(json.dumps({'scope':__doc__,'checks':checks},ensure_ascii=False,indent=2)+'\n')
print(f'Render source contracts: {len(checks)} passed. Not GPU or device verification.')
