import { readFileSync } from 'node:fs';
import { after, afterEach, before, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore';

const PARENT = 'parent1';
const OTHER_PARENT = 'parent2';
const CHILD = 'child1';
const OTHER_CHILD = 'child2';
const DEV = 'dev1';
const D = `users/${PARENT}/devices/${DEV}`;
const TOKEN = 'a'.repeat(32);

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-stayfocus',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => env.cleanup());
afterEach(async () => env.clearFirestore());

const parentDb = (uid = PARENT) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'password' } }).firestore();
const childDb = (uid = CHILD) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();
const anonDb = () => env.unauthenticatedContext().firestore();

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

async function seedDevice() {
  await seed(async (db) => {
    await setDoc(doc(db, D), {
      name: 'Kid phone', model: 'Pixel', linkedAt: Timestamp.now(), childUid: CHILD,
      online: false, battery: 50, charging: false, currentApp: null, lastSeen: null, focusEndsAt: null,
    });
    await setDoc(doc(db, `${D}/blocks/b1`), { type: 'LIMIT', name: 'Social', apps: ['a.b'], enabled: true });
    await setDoc(doc(db, `${D}/commands/c1`), { type: 'SYNC_BLOCKS', payload: {}, createdAt: Timestamp.now(), status: 'pending' });
    await setDoc(doc(db, `${D}/requests/r1`), { app: 'a.b', minutes: 15, status: 'pending', createdAt: Timestamp.now() });
    await setDoc(doc(db, `${D}/alerts/al1`), { kind: 'offline', createdAt: Timestamp.now(), dismissed: false });
    await setDoc(doc(db, `${D}/usage/2026-01-02`), { totalMins: 10, apps: [] });
    await setDoc(doc(db, `${D}/apps/a.b`), { label: 'App' });
    await setDoc(doc(db, `users/${PARENT}`), { displayName: 'P', email: 'p@example.com', createdAt: Timestamp.now() });
  });
}

const inMinutes = (m) => Timestamp.fromMillis(Date.now() + m * 60_000);
const tokenDoc = (over = {}) => ({
  childUid: CHILD, model: 'Pixel', createdAt: serverTimestamp(), expiresAt: inMinutes(4),
  code: '123456', status: 'open', ...over,
});

describe('default deny', () => {
  it('denies unauthenticated reads and writes', async () => {
    await seedDevice();
    await assertFails(getDoc(doc(anonDb(), D)));
    await assertFails(setDoc(doc(anonDb(), `users/${PARENT}`), { displayName: 'x', email: 'x@example.com', createdAt: serverTimestamp() }));
  });
  it('denies rateLimits to everyone', async () => {
    await assertFails(getDoc(doc(parentDb(), `rateLimits/${PARENT}_claim`)));
    await assertFails(setDoc(doc(parentDb(), `rateLimits/${PARENT}_claim`), { n: 1 }));
  });
  it('denies unknown top-level collections', async () => {
    await assertFails(setDoc(doc(parentDb(), 'misc/x'), { a: 1 }));
    await assertFails(getDoc(doc(parentDb(), 'misc/x')));
  });
  it('denies unlisted sub-collections', async () => {
    await assertFails(setDoc(doc(parentDb(), `users/${PARENT}/secrets/s1`), { a: 1 }));
  });
});

describe('parent profile', () => {
  const profile = () => ({ displayName: 'Pat', email: 'pat@example.com', createdAt: serverTimestamp() });
  it('creates its own profile', async () => {
    await assertSucceeds(setDoc(doc(parentDb(), `users/${PARENT}`), profile()));
  });
  it('rejects a profile with an extra field', async () => {
    await assertFails(setDoc(doc(parentDb(), `users/${PARENT}`), { ...profile(), admin: true }));
  });
  it('rejects a back-dated createdAt', async () => {
    await assertFails(setDoc(doc(parentDb(), `users/${PARENT}`), { ...profile(), createdAt: Timestamp.fromMillis(1000) }));
  });
  it('rejects an oversized displayName', async () => {
    await assertFails(setDoc(doc(parentDb(), `users/${PARENT}`), { ...profile(), displayName: 'x'.repeat(101) }));
  });
  it('rejects another user creating the profile', async () => {
    await assertFails(setDoc(doc(parentDb(OTHER_PARENT), `users/${PARENT}`), profile()));
  });
  it('rejects an anonymous user acting as the parent uid', async () => {
    await assertFails(setDoc(doc(childDb(PARENT), `users/${PARENT}`), profile()));
  });
  it('updates displayName but not createdAt', async () => {
    await seedDevice();
    await assertSucceeds(updateDoc(doc(parentDb(), `users/${PARENT}`), { displayName: 'New' }));
    await assertFails(updateDoc(doc(parentDb(), `users/${PARENT}`), { createdAt: serverTimestamp() }));
  });
  it('reads its own profile only', async () => {
    await seedDevice();
    await assertSucceeds(getDoc(doc(parentDb(), `users/${PARENT}`)));
    await assertFails(getDoc(doc(parentDb(OTHER_PARENT), `users/${PARENT}`)));
  });
  it('cannot delete its profile', async () => {
    await seedDevice();
    await assertFails(deleteDoc(doc(parentDb(), `users/${PARENT}`)));
  });
  it('the child cannot read the parent profile', async () => {
    await seedDevice();
    await assertFails(getDoc(doc(childDb(), `users/${PARENT}`)));
  });
});

describe('fcmTokens', () => {
  const path = `users/${PARENT}/fcmTokens/tok`;
  it('parent writes and deletes its own token', async () => {
    await assertSucceeds(setDoc(doc(parentDb(), path), { createdAt: serverTimestamp(), platform: 'android' }));
    await assertSucceeds(deleteDoc(doc(parentDb(), path)));
  });
  it('rejects a back-dated token createdAt', async () => {
    await assertFails(setDoc(doc(parentDb(), path), { createdAt: Timestamp.fromMillis(1000), platform: 'android' }));
  });
  it('rejects an unknown platform or extra field', async () => {
    await assertFails(setDoc(doc(parentDb(), path), { createdAt: serverTimestamp(), platform: 'ios' }));
    await assertFails(setDoc(doc(parentDb(), path), { createdAt: serverTimestamp(), platform: 'android', x: 1 }));
  });
  it('another parent cannot write the token', async () => {
    await assertFails(setDoc(doc(parentDb(OTHER_PARENT), path), { createdAt: serverTimestamp(), platform: 'android' }));
  });
});

describe('device docs', () => {
  it('parent reads own device; other parent and stranger cannot', async () => {
    await seedDevice();
    await assertSucceeds(getDoc(doc(parentDb(), D)));
    await assertFails(getDoc(doc(parentDb(OTHER_PARENT), D)));
    await assertFails(getDoc(doc(childDb(OTHER_CHILD), D)));
  });
  it('child reads its own device', async () => {
    await seedDevice();
    await assertSucceeds(getDoc(doc(childDb(), D)));
  });
  it('parent cannot create a device doc', async () => {
    await assertFails(setDoc(doc(parentDb(), `users/${PARENT}/devices/new`), { name: 'x', childUid: CHILD }));
  });
  it('child cannot create a device doc in the parent tree', async () => {
    await assertFails(setDoc(doc(childDb(), `users/${PARENT}/devices/new`), { name: 'x', childUid: CHILD }));
  });
  it('nobody can delete a device doc', async () => {
    await seedDevice();
    await assertFails(deleteDoc(doc(parentDb(), D)));
    await assertFails(deleteDoc(doc(childDb(), D)));
  });
  it('parent cannot update a device doc', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(parentDb(), D), { name: 'Renamed' }));
  });
  it('child updates its status fields', async () => {
    await seedDevice();
    await assertSucceeds(updateDoc(doc(childDb(), D), {
      battery: 80, charging: true, currentApp: 'a.b', online: true, lastSeen: serverTimestamp(),
    }));
  });
  it('child cannot change childUid or name', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(childDb(), D), { childUid: OTHER_CHILD }));
    await assertFails(updateDoc(doc(childDb(), D), { name: 'Hacked' }));
  });
  it('child mirrors focusEndsAt within 8 hours, or clears it', async () => {
    await seedDevice();
    await assertSucceeds(updateDoc(doc(childDb(), D), { focusEndsAt: inMinutes(30) }));
    await assertSucceeds(updateDoc(doc(childDb(), D), { focusEndsAt: null }));
    await assertFails(updateDoc(doc(childDb(), D), { focusEndsAt: inMinutes(60 * 24) }));
  });
  it('child cannot post a lastSeen other than the server time', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(childDb(), D), { lastSeen: inMinutes(600) }));
    await assertSucceeds(updateDoc(doc(childDb(), D), { battery: 40 }));
  });
  it('child rejects battery out of range or wrong type', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(childDb(), D), { battery: 101 }));
    await assertFails(updateDoc(doc(childDb(), D), { online: 'yes' }));
  });
  it('another child cannot update the status', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(childDb(OTHER_CHILD), D), { battery: 10 }));
  });
});

describe('collection queries', () => {
  it('parent lists its devices; another parent cannot', async () => {
    await seedDevice();
    await assertSucceeds(getDocs(collection(parentDb(), `users/${PARENT}/devices`)));
    await assertFails(getDocs(collection(parentDb(OTHER_PARENT), `users/${PARENT}/devices`)));
  });
  it('child lists pending commands on its device; another child cannot', async () => {
    await seedDevice();
    const q = (db) => query(collection(db, `${D}/commands`), where('status', '==', 'pending'));
    await assertSucceeds(getDocs(q(childDb())));
    await assertFails(getDocs(q(childDb(OTHER_CHILD))));
  });
  it('parent lists pending requests per device; another parent cannot', async () => {
    await seedDevice();
    const q = (db) => query(collection(db, `${D}/requests`), where('status', '==', 'pending'));
    await assertSucceeds(getDocs(q(parentDb())));
    await assertFails(getDocs(q(parentDb(OTHER_PARENT))));
  });
});

describe('blocks', () => {
  const block = (over = {}) => ({
    type: 'LIMIT', name: 'Games', apps: ['x.y'], enabled: true, limitMins: 30, period: 'DAILY', days: 127,
    createdAt: Timestamp.now(), source: 'MANUAL', ...over,
  });
  it('parent creates, updates and deletes a block', async () => {
    await seedDevice();
    await assertSucceeds(setDoc(doc(parentDb(), `${D}/blocks/b2`), block()));
    await assertSucceeds(updateDoc(doc(parentDb(), `${D}/blocks/b2`), { enabled: false }));
    await assertSucceeds(deleteDoc(doc(parentDb(), `${D}/blocks/b2`)));
  });
  it('rejects an invalid type or oversized name', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(parentDb(), `${D}/blocks/b2`), block({ type: 'FOO' })));
    await assertFails(setDoc(doc(parentDb(), `${D}/blocks/b2`), block({ name: 'n'.repeat(101) })));
  });
  it('rejects too many apps and out-of-range minutes', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(parentDb(), `${D}/blocks/b2`), block({ apps: Array.from({ length: 301 }, (_, i) => `p${i}`) })));
    await assertFails(setDoc(doc(parentDb(), `${D}/blocks/b2`), block({ limitMins: 5000 })));
  });
  it('rejects a block on a device that does not exist', async () => {
    await assertFails(setDoc(doc(parentDb(), `users/${PARENT}/devices/nope/blocks/b2`), block()));
  });
  it('rejects blocks missing type-specific fields, with bad enums or unknown keys', async () => {
    await seedDevice();
    const w = (b) => setDoc(doc(parentDb(), `${D}/blocks/b3`), b);
    await assertFails(w(block({ limitMins: null })));
    await assertFails(w(block({ period: 'WEEKLY' })));
    await assertFails(w(block({ source: 'HACK' })));
    await assertFails(w(block({ extra: 1 })));
    await assertFails(w(block({ type: 'SCHEDULE' })));
    await assertFails(w(block({ type: 'CYCLE', limitMins: null })));
    await assertFails(w(block({ type: 'NOW', limitMins: null })));
    await assertFails(w(block({ type: 'NOW', limitMins: null, durationMins: 60 })));
    await assertFails(w(block({ type: 'SCHEDULE', limitMins: null, range: {} })));
    await assertFails(w(block({ type: 'SCHEDULE', limitMins: null, range: { start: '25:00', end: '07:00' } })));
    await assertFails(w(block({ type: 'SCHEDULE', limitMins: null, range: { start: '08:00', end: '08:00' } })));
  });
  it('accepts a valid block of each type', async () => {
    await seedDevice();
    const w = (id, b) => setDoc(doc(parentDb(), `${D}/blocks/${id}`), b);
    await assertSucceeds(w('s', block({ type: 'SCHEDULE', limitMins: null, period: null, range: { start: '22:00', end: '07:00' } })));
    await assertSucceeds(w('c', block({ type: 'CYCLE', limitMins: null, period: null, useMins: 10, restMins: 30 })));
    await assertSucceeds(w('n', block({ type: 'NOW', limitMins: null, period: null, durationMins: 60, startedAt: Timestamp.now() })));
  });
  it('other parent cannot read or write blocks', async () => {
    await seedDevice();
    await assertFails(getDoc(doc(parentDb(OTHER_PARENT), `${D}/blocks/b1`)));
    await assertFails(setDoc(doc(parentDb(OTHER_PARENT), `${D}/blocks/b2`), block()));
  });
  it('child reads blocks but cannot write them', async () => {
    await seedDevice();
    await assertSucceeds(getDoc(doc(childDb(), `${D}/blocks/b1`)));
    await assertFails(updateDoc(doc(childDb(), `${D}/blocks/b1`), { enabled: false }));
    await assertFails(deleteDoc(doc(childDb(), `${D}/blocks/b1`)));
  });
  it('an unrelated child cannot read blocks', async () => {
    await seedDevice();
    await assertFails(getDoc(doc(childDb(OTHER_CHILD), `${D}/blocks/b1`)));
  });
});

describe('commands', () => {
  const cmd = (over = {}) => ({ type: 'BLOCK_APP', payload: { pkg: 'a.b' }, createdAt: serverTimestamp(), status: 'pending', ...over });
  it('parent creates a command', async () => {
    await seedDevice();
    await assertSucceeds(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd()));
  });
  it('rejects unknown type, non-pending status and extra fields', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd({ type: 'WIPE' })));
    await assertFails(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd({ status: 'done' })));
    await assertFails(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd({ extra: 1 })));
  });
  it('rejects an oversized payload', async () => {
    await seedDevice();
    const payload = Object.fromEntries(Array.from({ length: 11 }, (_, i) => [`k${i}`, i]));
    await assertFails(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd({ payload })));
    await assertFails(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd({ payload: { pkg: 'x'.repeat(256) } })));
    await assertFails(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd({ payload: { pkg: 'a.b', extra: 'x' } })));
    await assertFails(setDoc(doc(parentDb(), `${D}/commands/c2`), cmd({ type: 'START_FOCUS', payload: { mins: 9999 } })));
  });
  it('accepts a valid payload for each command type', async () => {
    await seedDevice();
    const ok = [
      ['START_FOCUS', { mins: 30 }], ['STOP_FOCUS', {}], ['SYNC_BLOCKS', {}], ['UNBLOCK_APP', { pkg: 'a.b' }],
      ['APPROVE_REQUEST', { reqId: 'r1', mins: 15 }], ['DENY_REQUEST', { reqId: 'r1' }],
      ['REMIND_PERMISSION', { permission: 'usage' }],
    ];
    for (const [i, [type, payload]] of ok.entries()) {
      await assertSucceeds(setDoc(doc(parentDb(), `${D}/commands/ok${i}`), cmd({ type, payload })));
    }
  });
  it('parent cannot update or delete a command', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(parentDb(), `${D}/commands/c1`), { status: 'done' }));
    await assertFails(deleteDoc(doc(parentDb(), `${D}/commands/c1`)));
  });
  it('other parent cannot create a command', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(parentDb(OTHER_PARENT), `${D}/commands/c2`), cmd()));
  });
  it('child cannot create a command', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(childDb(), `${D}/commands/c2`), cmd()));
  });
  it('child acknowledges with status and ackAt only', async () => {
    await seedDevice();
    await assertSucceeds(updateDoc(doc(childDb(), `${D}/commands/c1`), { status: 'done', ackAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(childDb(), `${D}/commands/c1`), { type: 'STOP_FOCUS' }));
    await assertFails(updateDoc(doc(childDb(), `${D}/commands/c1`), { status: 'pending' }));
  });
  it('child cannot change the outcome of a finished command', async () => {
    await seedDevice();
    await assertSucceeds(updateDoc(doc(childDb(), `${D}/commands/c1`), { status: 'done', ackAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(childDb(), `${D}/commands/c1`), { status: 'failed' }));
  });
  it('child reads commands; another child cannot', async () => {
    await seedDevice();
    await assertSucceeds(getDoc(doc(childDb(), `${D}/commands/c1`)));
    await assertFails(getDoc(doc(childDb(OTHER_CHILD), `${D}/commands/c1`)));
  });
});

describe('requests', () => {
  const req = (over = {}) => ({ app: 'a.b', minutes: 15, status: 'pending', createdAt: serverTimestamp(), ...over });
  it('child creates a valid request', async () => {
    await seedDevice();
    await assertSucceeds(setDoc(doc(childDb(), `${D}/requests/r2`), req()));
  });
  it('rejects an approved-on-create request, bad minutes, oversized app and extra fields', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(childDb(), `${D}/requests/r2`), req({ status: 'approved' })));
    await assertFails(setDoc(doc(childDb(), `${D}/requests/r2`), req({ minutes: 0 })));
    await assertFails(setDoc(doc(childDb(), `${D}/requests/r2`), req({ minutes: 9999 })));
    await assertFails(setDoc(doc(childDb(), `${D}/requests/r2`), req({ app: 'x'.repeat(256) })));
    await assertFails(setDoc(doc(childDb(), `${D}/requests/r2`), req({ decidedAt: serverTimestamp() })));
  });
  it('a different child cannot create a request', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(childDb(OTHER_CHILD), `${D}/requests/r2`), req()));
  });
  it('parent decides a pending request', async () => {
    await seedDevice();
    await assertSucceeds(updateDoc(doc(parentDb(), `${D}/requests/r1`), { status: 'approved', decidedAt: serverTimestamp() }));
  });
  it('parent cannot change other request fields or set a bogus status', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(parentDb(), `${D}/requests/r1`), { minutes: 480 }));
    await assertFails(updateDoc(doc(parentDb(), `${D}/requests/r1`), { status: 'pending', decidedAt: serverTimestamp() }));
  });
  it('child cannot decide its own request', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(childDb(), `${D}/requests/r1`), { status: 'approved', decidedAt: serverTimestamp() }));
  });
  it('other parent cannot decide a request', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(parentDb(OTHER_PARENT), `${D}/requests/r1`), { status: 'approved', decidedAt: serverTimestamp() }));
  });
});

describe('alerts', () => {
  const alert = (over = {}) => ({ kind: 'permission_lost', permission: 'usage', createdAt: serverTimestamp(), dismissed: false, ...over });
  it('child creates an alert', async () => {
    await seedDevice();
    await assertSucceeds(setDoc(doc(childDb(), `${D}/alerts/al2`), alert()));
  });
  it('rejects an unknown kind, pre-dismissed alert and oversized permission', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(childDb(), `${D}/alerts/al2`), alert({ kind: 'boom' })));
    await assertFails(setDoc(doc(childDb(), `${D}/alerts/al2`), alert({ dismissed: true })));
    await assertFails(setDoc(doc(childDb(), `${D}/alerts/al2`), alert({ permission: 'p'.repeat(51) })));
  });
  it('parent dismisses but cannot edit other fields', async () => {
    await seedDevice();
    await assertSucceeds(updateDoc(doc(parentDb(), `${D}/alerts/al1`), { dismissed: true }));
    await assertFails(updateDoc(doc(parentDb(), `${D}/alerts/al1`), { kind: 'unlinked' }));
  });
  it('child cannot dismiss or delete an alert', async () => {
    await seedDevice();
    await assertFails(updateDoc(doc(childDb(), `${D}/alerts/al1`), { dismissed: true }));
    await assertFails(deleteDoc(doc(childDb(), `${D}/alerts/al1`)));
  });
  it('another child cannot create an alert', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(childDb(OTHER_CHILD), `${D}/alerts/al2`), alert()));
  });
});

describe('usage and apps', () => {
  it('child writes usage for a date; parent reads it', async () => {
    await seedDevice();
    await assertSucceeds(setDoc(doc(childDb(), `${D}/usage/2026-02-03`), { totalMins: 90, apps: [{ pkg: 'a.b', label: 'A', mins: 90, opens: 3 }] }));
    await assertSucceeds(getDoc(doc(parentDb(), `${D}/usage/2026-02-03`)));
  });
  it('rejects a bad date id, oversized total, too many apps and extra fields', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(childDb(), `${D}/usage/today`), { totalMins: 1, apps: [] }));
    await assertFails(setDoc(doc(childDb(), `${D}/usage/2026-02-03`), { totalMins: 1441, apps: [] }));
    await assertFails(setDoc(doc(childDb(), `${D}/usage/2026-02-03`), { totalMins: 1, apps: Array.from({ length: 301 }, () => ({})) }));
    await assertFails(setDoc(doc(childDb(), `${D}/usage/2026-02-03`), { totalMins: 1, apps: [], x: 1 }));
  });
  it('parent cannot write usage; other parent cannot read it', async () => {
    await seedDevice();
    await assertFails(setDoc(doc(parentDb(), `${D}/usage/2026-02-03`), { totalMins: 1, apps: [] }));
    await assertFails(getDoc(doc(parentDb(OTHER_PARENT), `${D}/usage/2026-01-02`)));
  });
  it('child writes apps; rejects oversized label and extra fields', async () => {
    await seedDevice();
    await assertSucceeds(setDoc(doc(childDb(), `${D}/apps/c.d`), { label: 'Chat' }));
    await assertFails(setDoc(doc(childDb(), `${D}/apps/c.d`), { label: 'l'.repeat(101) }));
    await assertFails(setDoc(doc(childDb(), `${D}/apps/c.d`), { label: 'Chat', extra: 1 }));
  });
  it('parent reads apps but cannot write them', async () => {
    await seedDevice();
    await assertSucceeds(getDoc(doc(parentDb(), `${D}/apps/a.b`)));
    await assertFails(setDoc(doc(parentDb(), `${D}/apps/c.d`), { label: 'Chat' }));
  });
});

describe('linkTokens', () => {
  const path = `linkTokens/${TOKEN}`;
  it('child creates a valid token', async () => {
    await assertSucceeds(setDoc(doc(childDb(), path), tokenDoc()));
  });
  it('rejects a token with another childUid', async () => {
    await assertFails(setDoc(doc(childDb(), path), tokenDoc({ childUid: OTHER_CHILD })));
  });
  it('rejects expiry more than 5 minutes ahead', async () => {
    await assertFails(setDoc(doc(childDb(), path), tokenDoc({ expiresAt: inMinutes(10) })));
  });
  it('rejects an already expired token', async () => {
    await assertFails(setDoc(doc(childDb(), path), tokenDoc({ expiresAt: inMinutes(-1) })));
  });
  it('rejects claimedBy on create and non-open status', async () => {
    await assertFails(setDoc(doc(childDb(), path), tokenDoc({ claimedBy: PARENT })));
    await assertFails(setDoc(doc(childDb(), path), tokenDoc({ status: 'claimed' })));
  });
  it('rejects a malformed code or a short token id', async () => {
    await assertFails(setDoc(doc(childDb(), path), tokenDoc({ code: '12ab56' })));
    await assertFails(setDoc(doc(childDb(), 'linkTokens/short'), tokenDoc()));
  });
  it('rejects unauthenticated creation', async () => {
    await assertFails(setDoc(doc(anonDb(), path), tokenDoc()));
  });
  it('only the owning child can read; nobody can update or delete', async () => {
    await seed(async (db) => setDoc(doc(db, path), { ...tokenDoc(), createdAt: Timestamp.now() }));
    await assertSucceeds(getDoc(doc(childDb(), path)));
    await assertFails(getDoc(doc(childDb(OTHER_CHILD), path)));
    await assertFails(getDoc(doc(parentDb(), path)));
    await assertFails(updateDoc(doc(childDb(), path), { status: 'used' }));
    await assertFails(updateDoc(doc(parentDb(), path), { claimedBy: PARENT }));
    await assertFails(deleteDoc(doc(childDb(), path)));
  });
});
