# Abyss Anmap notices

`Abyss Anmap` is an independent Android front end. It is not affiliated with,
endorsed by, or an official product of Nmap Software LLC or the Vulscan project.

It builds and runs the Nmap source supplied in `third_party/nmap` and bundles
the supplied Vulscan 2.1 script and offline data in `third_party/vulscan`.
The application shows an attribution notice at runtime and contains the full
license texts in both the repository and APK assets.

The Nmap Public Source License treats a front end designed specifically to
execute Nmap or include Nmap data as a derivative work. Consequently, do not
publish a proprietary or commercial APK from this project without first
checking the NPSL obligations and, where applicable, arranging a separate Nmap
OEM licence. The full source tree and notices are intentionally retained here.

Vulscan's supplied `COPYING.TXT` is GPL-3.0. A Vulscan match is only a possible
vulnerability correlation; it is not proof of a vulnerability and this app
does not attempt exploitation.

The supplied Nmap `script.db` names `sap-hana-auth.nse`, but that file is absent
from the supplied Nmap source. The runtime copy omits only that stale database
row so the bundled script catalogue exactly matches the 611 supplied `.nse`
files.
