# Consent, legality & responsible use

This project is built for **one lawful purpose**: a parent/guardian monitoring a
**minor child** on a device the parent owns or is responsible for.

## The rules this app follows

1. **It stays visible.** The app keeps its icon in the app drawer and shows a permanent
   notification while monitoring. It does not conceal itself. (Google Play policy also
   requires this of any monitoring app, and hiding would make it stalkerware.)
2. **It is consent-based.** The setup flow shows the child exactly what is collected.
   Best practice — and the law in many places — is that the child knows.
3. **It minimizes data.** It records *which* apps and websites and *where*, not the
   *content* of anything. No messages, no notification text, no camera, no microphone.
4. **It keeps data local.** No cloud. Data is readable only from your own phone on your
   own Wi-Fi, protected by your PIN.

## Where this is and isn't appropriate

| Appropriate | Not appropriate (do not use) |
|---|---|
| Your minor child's phone | A partner's / spouse's phone |
| A device you legally own & administer | An employee's personal phone |
| With the child's awareness | Any adult who hasn't consented |

Monitoring an adult without their knowledge/consent can violate wiretapping, computer-
misuse, stalking, and privacy laws (e.g. US ECPA & state laws, UK Computer Misuse Act,
EU GDPR, India IT Act). **Laws vary — if unsure, check your local law.** The authors
provide this for lawful parental use only and are not responsible for misuse.

## As your child grows

Consider tapering monitoring as kids get older, and talking about online safety rather
than relying on surveillance alone. Transparency tends to keep kids safer than secrecy,
because they'll come to you when something goes wrong.

## Data handling summary

- **Stored:** app-usage aggregates, visited URLs/hosts, location fixes — on the child's
  phone only, in an app-private database, pruned after 30 days.
- **Transmitted:** only over your local network, only to a client that presents the
  correct PIN-derived token.
- **Never collected:** message/SMS/chat content, notification text, keystrokes,
  photos, audio, or video.
