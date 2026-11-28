# Release signing: what the keys are, and how to get them back

The release build is signed on the Jenkins server from four credentials. This
page says what they are, how severe losing them actually is, and what to do
about it.

---

## What the key actually is

It is the **upload key**, not the app signing key.

`life.uncomplicated` is a new listing and Play required an App Bundle, which
means it is enrolled in **Play App Signing**: Google holds the app signing key
and re-signs every install on their side. What Jenkins holds is the key used to
sign the bundle on the way *in*.

That distinction is the whole risk assessment:

| | Upload key (what we have) | App signing key (what Google has) |
|---|---|---|
| Who holds it | us, on Jenkins | Google |
| If lost | request a reset, ship again in a few days | the listing is unrecoverable |
| Our exposure | inconvenience | none, as long as Play App Signing stays on |

**Verify once, if it has never been checked:** Play Console → Test and release →
Setup → App signing. It should say Play App Signing is enabled. If it somehow
does not, this page's risk section is wrong and losing the key is permanent.

**The escape hatch**, if the key is ever genuinely gone: generate a new keystore,
then use Play Console's upload key reset (Setup → App signing → request upload
key reset). Google swaps which key they accept for uploads. Nobody has to
reinstall anything, because the app signing key never changed.

---

## The four credentials

All on the Jenkins server, under *Manage Jenkins → Credentials → System →
Global*:

| Credential ID | Type | Holds |
|---|---|---|
| `android-release-keystore` | Secret file | `keystore.jks` |
| `android-keystore-password` | Secret text | the store password |
| `android-key-alias` | Secret text | which key inside the store |
| `android-key-password` | Secret text | the key password |

`app/build.gradle.kts` reads all four from the environment; `Jenkinsfile` binds
them with `withCredentials` only inside the release stage. A build without them
produces `app-release-unsigned.apk`, and the pipeline fails on purpose rather
than archiving it.

**A keystore without its three passwords is a scrambled file.** Back them up
together or do not bother.

---

## Identifying the key

Two different digests, easy to confuse:

- **Certificate SHA-256** — identifies the *key*, and is what Play and
  `apksigner` report:
  `84c0fe698a4d8231efee077d58162de3bfbdddc2cb4358e9382dfebfb07c4440`
- **File SHA-256** of `keystore.jks` — identifies *that copy of the file*, and is
  only useful for checking a transfer:
  `4ac9b137386766ad73dc129b8481c247f1f49e219ee6e620b5af95576bb9f77a`

Certificate subject: `O=365 Build Technologies Pvt ltd, L=Bangalore, ST=KA, C=IN`

To check any keystore is the right one:

```bash
keytool -list -v -keystore <file> | grep -i 'SHA256:'
```

To check a built APK was signed with it:

```bash
apksigner verify --print-certs app-release.apk
```

---

## Exporting from Jenkins

Jenkins will not let you download a Secret file through the UI. The script
console will, and the rule is that **key material must never reach a build log**
— logs are retained for 15 builds and readable by anyone with job access.

Run in *Manage Jenkins → Script Console*, or POST to `/scriptText`:

```groovy
import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

def DIR_PERMS  = PosixFilePermissions.fromString("rwx------")
def FILE_PERMS = PosixFilePermissions.fromString("rw-------")

def dir = Paths.get("/var/lib/jenkins/key-backup")
if (Files.exists(dir)) Files.setPosixFilePermissions(dir, DIR_PERMS)
else Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(DIR_PERMS))

def creds = CredentialsProvider.lookupCredentials(StandardCredentials.class, Jenkins.get(), null, null)
def find = { String id ->
    def c = creds.find { it.id == id }
    if (c == null) throw new IllegalStateException("credential not found: " + id)
    c
}

def ksCred = find("android-release-keystore")
byte[] bytes = ksCred.getContent().withCloseable { it.bytes }
def ksPath = dir.resolve("uncomplicated-upload-key.jks")
Files.write(ksPath, bytes)
Files.setPosixFilePermissions(ksPath, FILE_PERMS)

def props = new StringBuilder()
props.append("# Original filename in Jenkins: ").append(ksCred.getFileName()).append("\n\n")
props.append("RELEASE_STORE_PASSWORD=").append(find("android-keystore-password").getSecret().getPlainText()).append("\n")
props.append("RELEASE_KEY_ALIAS=").append(find("android-key-alias").getSecret().getPlainText()).append("\n")
props.append("RELEASE_KEY_PASSWORD=").append(find("android-key-password").getSecret().getPlainText()).append("\n")
def propsPath = dir.resolve("signing.properties")
Files.write(propsPath, props.toString().getBytes("UTF-8"))
Files.setPosixFilePermissions(propsPath, FILE_PERMS)

def sha = MessageDigest.getInstance("SHA-256").digest(bytes).collect { String.format("%02x", it) }.join()
println "wrote ${bytes.length} bytes, sha256 ${sha}"
```

It prints a path, a size and a checksum. Nothing secret.

Then copy both files off — they are `0600` and owned by `jenkins`, so this needs
sudo:

```bash
ssh HOST 'sudo cat /var/lib/jenkins/key-backup/uncomplicated-upload-key.jks' > uncomplicated-upload-key.jks
ssh HOST 'sudo cat /var/lib/jenkins/key-backup/signing.properties'           > signing.properties
sha256sum uncomplicated-upload-key.jks   # compare with the checksum printed above
```

And **delete the staging copy** once verified:

```groovy
def dir = new File("/var/lib/jenkins/key-backup")
dir.listFiles()?.each { f ->
    def len = f.length()
    f.withOutputStream { out -> out.write(new byte[(int) len]) }   // overwrite first
    f.delete()
}
dir.delete()
println "cleaned: " + !dir.exists()
```

---

## Where the backup lives

Encrypted, in at least two places, one of which is not the NAS:

- `Personal-Docs` on the Common-Share, beside the ssh keys.
- A second copy somewhere independent of that disk.

Encrypt at rest — `age`, `gpg`, or a 7z archive with AES-256. The keystore and
its passwords sitting in plaintext in one folder is a single-step compromise;
putting the three secrets in a password manager instead, with the `.jks` as an
attachment, is better than a `.properties` file next to the key.

**Never commit any of it.** Nothing matching `*.jks` or `*.keystore` has ever
been in this repo's history, and it should stay that way.

---

## Restoring into a fresh Jenkins

1. *Manage Jenkins → Credentials → System → Global → Add Credentials*
2. Recreate all four with **exactly** the IDs in the table above — the
   `Jenkinsfile` looks them up by ID, so a typo fails the release stage with a
   credential-not-found error.
3. Run the job with `RELEASE=true`.
4. Confirm the pipeline's own checks passed: `apksigner verify` reports
   `Verifies`, and `jarsigner -verify` on the bundle reports `jar verified.`
   Both already run in the release stage and fail the build if they do not.
5. Confirm the certificate digest still matches the one above. If it does not,
   the build is signed with the wrong key and Play will reject the upload.
