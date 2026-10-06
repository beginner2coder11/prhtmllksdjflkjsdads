# Showing rewarded ads from your HTML

Apps built with ads expose a global `AndroidAds` object to the bundled page. A banner
and the every-Nth-page interstitial need nothing from you; rewarded ads do, because
only your page knows what it is offering and when the user accepted.

## The API

```js
AndroidAds.isRewardedReady()                 // boolean — gate your button on this
AndroidAds.showRewarded("coins_50")          // the tag comes back to you untouched
AndroidAds.isRewardedInterstitialReady()
AndroidAds.showRewardedInterstitial("level_2")
AndroidAds.showInterstitial()                // optional manual interstitial
```

Define any of these to hear back. All are optional.

```js
window.onAdReward      = r => { /* {tag, type, amount} — the ad WAS watched */ };
window.onAdClosed      = r => { /* {tag} — dismissed, rewarded or not        */ };
window.onAdUnavailable = r => { /* {tag} — nothing was ready to show         */ };
```

**Hand out the reward in `onAdReward` and nowhere else.** It is the only callback that
means the user watched to the reward point. `onAdClosed` fires whether they earned it
or not — a user who closes the ad after two seconds reaches `onAdClosed` only.

## A complete example

```html
<button id="watch" disabled>Watch an ad for 50 coins</button>
<p id="balance">Coins: 0</p>

<script>
  var coins = 0;
  var button = document.getElementById('watch');

  // The object only exists inside a built app that has ads configured, so guard for
  // it — in a browser, or an app built without ads, this block simply does nothing.
  var ads = window.AndroidAds;

  function refresh() {
    button.disabled = !(ads && ads.isRewardedReady());
  }

  button.onclick = function () {
    if (ads && ads.isRewardedReady()) ads.showRewarded('coins_50');
  };

  window.onAdReward = function (r) {
    if (r.tag === 'coins_50') {
      coins += 50;                    // r.amount / r.type come from your AdMob unit
      document.getElementById('balance').textContent = 'Coins: ' + coins;
    }
  };

  window.onAdClosed      = function () { refresh(); };
  window.onAdUnavailable = function () { refresh(); };

  refresh();
  setInterval(refresh, 2000);         // the next ad loads in the background
</script>
```

## Rules worth knowing

- **The user must choose it.** A rewarded ad has to be opt-in: a button that says what
  they get. Showing one unprompted breaks AdMob policy.
- **Never assume it is ready.** Check `isRewardedReady()` and disable the button when
  it is false. The next ad starts loading as soon as one is dismissed, so a disabled
  button usually becomes enabled again within seconds.
- **Only the bundled site can call this.** If your app has tab bar entries pointing at
  real websites, those pages cannot use `AndroidAds` — the bridge refuses calls from
  any origin but your own content.
- **Nothing exists without configuration.** `window.AndroidAds` is undefined in an app
  built with no AdMob ids, so always guard as the example does.
