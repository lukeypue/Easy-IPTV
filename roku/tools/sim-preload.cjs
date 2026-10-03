// Containers may deny interface enumeration. Keep simulator discovery on loopback.
const os = require('node:os');
const real = os.networkInterfaces;
os.networkInterfaces = () => { try { return real(); } catch { return {lo:[{address:'127.0.0.1',netmask:'255.0.0.0',family:'IPv4',mac:'00:00:00:00:00:00',internal:true,cidr:'127.0.0.1/8'}]}; } };
