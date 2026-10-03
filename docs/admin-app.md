# The admin app on children's phones

For newspaper operators, parents and teachers. This page explains why Google Play may refuse to
install the Presserl admin app on a child's phone, and what you can do about it.

## 1. Does this affect you?

Probably yes, if all of these are true:

- The phone belongs to a child whose Google account is managed by a parent with **Google Family
  Link** (the parent sets screen time and allowed apps from their own phone).
- Installing the Presserl app from Google Play fails with one of these messages, although the
  phone is connected to the internet:
  - "An error occurred. Turn on Wi-Fi or mobile data and try again"
  - "Item not found" (German: "Nicht gefunden")
- The parent's Family Link app shows **no** request to approve the app.

The message about Wi-Fi is misleading: the internet connection is fine. Google Play just does not
say the real reason.

## 2. Why it happens

Google Play labels every app with an age rating, like films and games. In Europe the label comes
from an organisation called PEGI.

In the Presserl app, children and adults write articles and upload photos. Because this content is
written by people and not by the app maker, PEGI cannot give the app a fixed age. Instead it labels
the app **"Parental guidance"**. You see this label on the app's page in the Play Store.

Family Link lets parents allow apps only up to a certain age, for example "up to 12". An app with
the label "Parental guidance" has no age, so Family Link treats it as not allowed — and blocks it
without telling anyone. Approving the app as a parent does not help, because the request never
reaches you.

## 3. What to do

On the **parent's phone**, in the Family Link app (German labels in brackets):

1. Select your child.
2. Tap **Controls** (*Steuerelemente*).
3. Tap **Content restrictions** (*Inhaltsbeschränkungen*).
4. Tap **Google Play**.
5. Tap **Apps & games** (*Apps und Spiele*).
6. Choose **Allow all** (*Alle zulassen*).

Then, on the **child's phone**, open Google Play and install the Presserl app again. If it still
fails, wait a few minutes and try once more.

The names of these menus change from time to time with new versions of Family Link and may look
a little different on your phone. Look for the setting that limits apps by age.

## 4. What this setting means

"Allow all" applies to **every app** on your child's phone, not only to Presserl. Apps of every
age rating can then be installed, including apps for adults.

We recommend keeping **approvals** on: in the same Google Play settings, choose that your child
needs your approval for new apps (*Genehmigung erforderlich*). Then you still decide app by app
what your child installs. We have not yet been able to confirm that the approval request for the
Presserl app actually reaches you with this combination. If it does not, you have to choose
between "Allow all" without approvals and one of the alternatives below.

If you do not want to change the setting, that is a perfectly good choice. Use one of the
alternatives.

## 5. Alternatives

Both work without changing anything in Family Link:

- **Another phone or tablet.** Install the app on a parent's phone or on a school tablet, and let
  your child log in there with their own newspaper account. The phone is only a tool; the articles
  belong to your child's account.
- **The browser.** The admin app also runs in a web browser on a computer. Open
  `https://<newspaper address>/admin/` (the address of your newspaper with `/admin/` at the end)
  and log in with the same username and password. Ask the person who runs your newspaper for the
  address if you do not know it.

## 6. During the test phase

At the moment the Android app is only available to invited testers (Google calls this a "test
track": testers join through an opt-in link). Phones with Family Link may not be able to install
such test versions at all, even with "Allow all". This is not confirmed yet. If installing still
fails after step 3, please use one of the alternatives in section 5.

This section will be updated once the app is available to everyone in Google Play.

---

For developers: details and a troubleshooting checklist are in
[Google Play Console — Content rating](play-console.md#content-rating-iarc-questionnaire) and
[Install problems](play-console.md#install-problems).
