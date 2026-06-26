# Production Architecture Guide: GreatShort Video Earning Platform

This guide contains complete architectural specifications, production-ready source code scripts, database security rules, payment gateway integration patterns (JazzCash/Easypaisa), cloud video transcoding pipelines, and anti-fraud systems required to scale GreatShort to a high-volume production environment.

---

## 1. Concrete Database Schemas & Double-Entry Ledger System

In FinTech, using simple flat fields like `availableBalance = availableBalance + X` is prone to race conditions and double-spending. You must implement an **immutable double-entry ledger database pattern** in Firestore.

### 1.1 Ledger Schema Definition
Each balance-changing operation must record an entry into `/wallet_ledger/{ledgerId}`:

```typescript
export interface LedgerEntry {
  id: string;               // UUID of ledger transaction
  userId: string;           // Target user
  amount: number;           // Positive (credit) or negative (debit)
  currency: 'PKR';
  type: 'watch_reward' | 'referral_signup' | 'streak_bonus' | 'withdrawal_debit' | 'refund_failed_withdrawal';
  referenceId: string;      // ID of watch_event, referral transaction, or withdrawal request
  status: 'cleared' | 'hold' | 'reversed';
  timestamp: FirebaseFirestore.Timestamp;
  hash: string;             // Integrity hash (HMAC of userId + amount + type + timestamp + previousHash)
}
```

### 1.2 Thread-Safe Multi-Account Balance Reconciliation
To obtain a user's exact balance, run an aggregation query or maintain a cached balance in `/wallet_accounts/{userId}` strictly within a Firestore Transaction:

```typescript
import { db, collections } from '../config/firebase';
import crypto from 'crypto';

export async function processLedgerTransaction(
  userId: string, 
  amount: number, 
  type: LedgerEntry['type'], 
  refId: string, 
  description: string
) {
  const walletRef = db.collection('wallet_accounts').doc(userId);
  const ledgerRef = db.collection('wallet_ledger').doc();

  await db.runTransaction(async (transaction) => {
    const walletSnap = await transaction.get(walletRef);
    let wallet = walletSnap.exists 
      ? walletSnap.data() 
      : { userId, availableBalance: 0, pendingBalance: 0, lifetimeEarned: 0, totalWithdrawn: 0 };

    // Prevent negative balance on debit/withdrawals
    if (amount < 0 && wallet!.availableBalance < Math.abs(amount)) {
      throw new Error('INSUFFICIENT_FUNDS');
    }

    // Update wallet cache balances
    const newAvailable = wallet!.availableBalance + amount;
    const newLifetime = amount > 0 ? (wallet!.lifetimeEarned + amount) : wallet!.lifetimeEarned;

    const ledgerEntry: LedgerEntry = {
      id: ledgerRef.id,
      userId,
      amount,
      currency: 'PKR',
      type,
      referenceId: refId,
      status: 'cleared',
      timestamp: FirebaseFirestore.Timestamp.now(),
      hash: crypto.createHmac('sha256', process.env.LEDGER_SECRET_KEY || 'pkr_secret')
        .update(`${userId}:${amount}:${type}:${refId}`)
        .digest('hex')
    };

    transaction.set(ledgerRef, ledgerEntry);
    transaction.set(walletRef, {
      ...wallet,
      availableBalance: newAvailable,
      lifetimeEarned: newLifetime,
      updatedAt: FirebaseFirestore.Timestamp.now()
    }, { merge: true });
  });
}
```

---

## 2. JazzCash & Easypaisa Integration Patterns

Both Pakistan-based mobile wallets require calculating secure HMAC signatures before redirecting the client or calling direct API merchant gateways.

### 2.1 JazzCash IPG Merchant API Client (HMAC-SHA256)
JazzCash checkout forms require submitting parameters in sorted alphabetical order with a secret salt:

```typescript
import crypto from 'crypto';

export class JazzCashService {
  private static MERCHANT_ID = process.env.JAZZCASH_MERCHANT_ID || 'MC12345';
  private static PASSWORD = process.env.JAZZCASH_PASSWORD || 'pwd123';
  private static INTEGRITY_SALT = process.env.JAZZCASH_INTEGRITY_SALT || 'saltXYZ';
  private static API_URL = process.env.NODE_ENV === 'production' 
    ? 'https://payments.jazzcash.com.pk/CustomerPortal/transaction/pay' 
    : 'https://sandbox.jazzcash.com.pk/CustomerPortal/transaction/pay';

  /**
   * Generate payload and secure cryptographic signature for client checkout
   */
  static generatePaymentRequest(orderId: string, amount: number, mobileNo: string) {
    const amountInPaisa = amount * 100; // JazzCash expects integer value representing Paisa
    const timestamp = new Date().toISOString().replace(/[-:T.Z]/g, '');
    const expiry = new Date(Date.now() + 3600 * 1000).toISOString().replace(/[-:T.Z]/g, ''); // 1hr expiry

    const params: Record<string, string> = {
      pp_Version: '1.1',
      pp_TxnType: 'MWALLET',
      pp_Language: 'EN',
      pp_MerchantID: this.MERCHANT_ID,
      pp_SubMerchantID: '',
      pp_Password: this.PASSWORD,
      pp_BankID: '',
      pp_ProductID: '',
      pp_TxnRefNo: orderId,
      pp_Amount: amountInPaisa.toString(),
      pp_TxnCurrency: 'PKR',
      pp_TxnDateTime: timestamp,
      pp_BillReference: orderId,
      pp_Description: 'GreatShort Video Reward Cashout',
      pp_TxnExpiryDateTime: expiry,
      pp_SecureHash: '',
      pp_MobileNumber: mobileNo,
      pp_CNIC: '3520112345673', // Placeholder CNIC or dynamic from User Profile
    };

    // Sort parameters alphabetically to construct validation string
    const sortedKeys = Object.keys(params).sort();
    let sortedString = this.INTEGRITY_SALT;
    for (const key of sortedKeys) {
      if (params[key] !== '' && key !== 'pp_SecureHash') {
        sortedString += `&${params[key]}`;
      }
    }

    params.pp_SecureHash = crypto
      .createHmac('sha256', this.INTEGRITY_SALT)
      .update(sortedString)
      .digest('hex')
      .toUpperCase();

    return { url: this.API_URL, payload: params };
  }
}
```

### 2.2 Secure Gateway Postback Webhook Listener
When a customer completes a deposit or when a payout dispatch is confirmed, the mobile wallet will hit your callback endpoint. Secure this endpoint against replay attacks:

```typescript
import { Request, Response } from 'express';
import crypto from 'crypto';
import { processLedgerTransaction } from './ledger';

export async function handleJazzCashWebhook(req: Request, res: Response) {
  const payload = req.body;
  const secureHash = payload.pp_SecureHash;

  if (!secureHash) {
    return res.status(400).json({ error: 'Missing security verification payload' });
  }

  // 1. Verify integrity hash
  const salt = process.env.JAZZCASH_INTEGRITY_SALT || 'saltXYZ';
  const sortedKeys = Object.keys(payload).sort();
  let verificationString = salt;
  for (const key of sortedKeys) {
    if (payload[key] !== '' && key !== 'pp_SecureHash') {
      verificationString += `&${payload[key]}`;
    }
  }

  const calculatedHash = crypto
    .createHmac('sha256', salt)
    .update(verificationString)
    .digest('hex')
    .toUpperCase();

  if (calculatedHash !== secureHash) {
    return res.status(401).json({ error: 'Tampering detected. Secure hash mismatch.' });
  }

  // 2. Extract transaction results
  const txnRef = payload.pp_TxnRefNo;
  const responseCode = payload.pp_ResponseCode; // "000" indicates success
  const amount = parseFloat(payload.pp_Amount) / 100;

  if (responseCode === '000') {
    // Process internal financial bookkeeping securely
    await processLedgerTransaction(payload.pp_BillReference, amount, 'watch_reward', txnRef, 'Wallet direct deposit approved');
    return res.status(200).send('OK');
  } else {
    console.warn(`Payment failure callback received for Ref ${txnRef}: Code ${responseCode}`);
    return res.status(200).send('Processed Failure');
  }
}
```

---

## 3. High-Performance Video Transcoding Pipeline

For production short video platforms, uploading raw 4K/1080p videos directly leads to extreme bandwidth costs and laggy playback in rural areas with 3G/4G connections. You must run videos through **HLS/Dash adaptive bitrate transcoding**.

### 3.1 Transcoder Architecture Blueprint
1. User uploads video directly to an isolated Google Cloud Storage (GCS) upload bucket (`gs://greatshort-raw-uploads`) using pre-signed secure URLs.
2. An event-driven Cloud Function triggers immediately upon upload.
3. The function dispatches a job to **Google Cloud Transcoder API** to generate multi-resolution adaptive streaming folders (1080p, 720p, 480p) inside a public CDN-backed distribution bucket (`gs://greatshort-processed-media`).

### 3.2 Automated Transcoder Dispatch Script (Node.js)
```typescript
import { TranscoderServiceClient } from '@google-cloud/video-transcoder';

const transcoderClient = new TranscoderServiceClient();

export async function triggerAdaptiveTranscodeJob(videoFileName: string) {
  const projectId = process.env.GCP_PROJECT_ID || 'greatshort-prod';
  const location = 'us-central1';
  const inputUri = `gs://greatshort-raw-uploads/${videoFileName}`;
  const outputUri = `gs://greatshort-processed-media/${videoFileName.split('.')[0]}/`;

  const request = {
    parent: transcoderClient.locationPath(projectId, location),
    job: {
      inputUri,
      outputUri,
      config: {
        // HLS (HTTP Live Streaming) playlist profiles
        elementaryStreams: [
          {
            key: 'video_720p',
            videoStream: {
              h264: {
                widthPixels: 720,
                heightPixels: 1280, // Portrait orientation for Reels/Shorts
                bitrateBps: 1500000,
                frameRate: 30,
              },
            },
          },
          {
            key: 'video_480p',
            videoStream: {
              h264: {
                widthPixels: 480,
                heightPixels: 854,
                bitrateBps: 800000,
                frameRate: 24,
              },
            },
          },
          {
            key: 'audio_main',
            audioStream: {
              codec: 'aac',
              bitrateBps: 64000,
            },
          },
        ],
        muxStreams: [
          {
            key: 'stream_720p',
            container: 'ts',
            elementaryStreams: ['video_720p', 'audio_main'],
          },
          {
            key: 'stream_480p',
            container: 'ts',
            elementaryStreams: ['video_480p', 'audio_main'],
          },
        ],
        playlists: [
          {
            key: 'playlist_hls',
            fileName: 'manifest.m3u8',
            type: 'HLS',
            variantStreamKeys: ['stream_720p', 'stream_480p'],
          },
        ],
      },
    },
  };

  const [job] = await transcoderClient.createJob(request);
  console.log(`Successfully queued HLS transcode job: ${job.name}`);
  return job.name;
}
```

---

## 4. Multi-Layer Anti-Fraud Engine

Anti-fraud is critical to avoid payout bankruptcies. Real-time watch events must pass a severe series of cryptographic and contextual checks before a single rupee is awarded.

### 4.1 Fingerprint Cryptographic Verification
When the client claims watched video time, the Android app generates a device fingerprint and a localized signed hash that is checked server-side:

```typescript
import { Request, Response } from 'express';
import { collections } from '../config/firebase';

export class AntiFraudEngine {
  /**
   * Score security indicators and block suspicious payouts
   */
  static async verifyWatchSession(
    userId: string, 
    videoId: string, 
    watchedSeconds: number, 
    completionPercentage: number,
    ipAddress: string,
    userAgent: string,
    fingerprint: { isEmulator: boolean; osVersion: string; hardware: string }
  ): Promise<{ isValid: boolean; riskScore: number; reason?: string }> {
    
    // Check 1: Simple physical speed impossibility
    if (watchedSeconds > 300) { // Limit individual clips to 5 mins max per event
      return { isValid: false, riskScore: 100, reason: 'EXCESSIVE_SINGLE_CLIP_WATCH_DURATION' };
    }

    // Check 2: Device Simulator / Emulator signature matching
    if (fingerprint.isEmulator) {
      await this.logFraudSignal(userId, 'emulator_fingerprint', 'high', 'Execution in root/emulated machine detected');
      return { isValid: false, riskScore: 100, reason: 'EMULATOR_SANDBOX_RESTRICTED' };
    }

    // Check 3: Multi-Account Sybil detection on same physical connection
    const concurrentIPCount = await collections.watch_events
      .where('ipAddress', '==', ipAddress)
      .where('startTime', '>=', FirebaseFirestore.Timestamp.fromDate(new Date(Date.now() - 3600 * 1000))) // last 1 hour
      .get();

    const uniqueUsersOnIP = new Set(concurrentIPCount.docs.map(doc => doc.data().userId));
    if (uniqueUsersOnIP.size > 3) {
      await this.logFraudSignal(userId, 'duplicate_session', 'medium', `IP farming detected: ${uniqueUsersOnIP.size} accounts active`);
      return { isValid: true, riskScore: 65, reason: 'SHARED_IP_IP_FARMING_DETECTION' };
    }

    // Check 4: Check if total hours watched exceeds a physical 24h limit (e.g., maximum 4 hours of reward watching a day)
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const dayWatches = await collections.watch_events
      .where('userId', '==', userId)
      .where('startTime', '>=', FirebaseFirestore.Timestamp.fromDate(today))
      .get();

    let totalMinutesWatchedToday = 0;
    dayWatches.forEach(doc => {
      totalMinutesWatchedToday += (doc.data().watchedSeconds || 0) / 60;
    });

    if (totalMinutesWatchedToday > 240) { // 4 Hours limit
      return { isValid: false, riskScore: 80, reason: 'DAILY_REWARD_DURATION_EXCEEDED' };
    }

    return { isValid: true, riskScore: 10 };
  }

  private static async logFraudSignal(userId: string, type: string, severity: 'low' | 'medium' | 'high', details: string) {
    const signalRef = collections.fraud_signals.doc();
    await signalRef.set({
      id: signalRef.id,
      userId,
      signalType: type,
      severity,
      riskScoreImpact: severity === 'high' ? 80 : 30,
      details,
      timestamp: FirebaseFirestore.Timestamp.now(),
      isResolved: false
    });
  }
}
```

---

## 5. Scalability & Event Queue Handling (Bull-MQ Redis)

In production, running background transcoding, push notifications, and balance audits inside the HTTP cycle leads to requests dropping and hitting the express request timeout. Use **BullMQ + Redis** as an event-driven queue executor.

### 5.1 Setting up BullMQ Redis Queues
```typescript
import { Queue, Worker } from 'bullmq';
import IORedis from 'ioredis';

const redisConnection = new IORedis(process.env.REDIS_URL || 'redis://127.0.0.1:6379');

// Declare job queues
export const notificationQueue = new Queue('push_notifications', { connection: redisConnection });
export const videoTranscodeQueue = new Queue('video_transcoding', { connection: redisConnection });

// Set up background process workers
const notificationWorker = new Worker('push_notifications', async (job) => {
  const { userId, title, message } = job.data;
  console.log(`Processing push dispatch for user ${userId}: ${title}`);
  // Dispatch via Firebase Admin FCM
}, { connection: redisConnection });
```

---

## 6. Real-Time Security Rules for Firestore Protection

Ensure that no client can manually set their balance or approve withdrawals. Deploy this exact policy into your Firebase Console to lock write permissions down strictly to your secure Express backend admin account:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isAuthenticated() { return request.auth != null; }
    function isOwner(userId) { return request.auth.uid == userId; }
    function isAdmin() { return isAuthenticated() && request.auth.token.role == 'admin'; }

    match /users/{userId} {
      allow read: if isAuthenticated();
      allow write: if isAdmin() || (isOwner(userId) && !request.resource.data.isBlocked);
    }
    match /wallet_accounts/{userId} {
      allow read: if isAuthenticated() && isOwner(userId);
      allow write: if isAdmin(); // Server-side operations ONLY
    }
    match /wallet_ledger/{ledgerId} {
      allow read: if isAuthenticated() && resource.data.userId == request.auth.uid;
      allow write: if isAdmin(); // Server-side ledger ONLY
    }
    match /withdrawals/{wId} {
      allow read: if isAuthenticated() && (resource.data.userId == request.auth.uid || isAdmin());
      allow write: if isAdmin(); // Server-side dispatch approval ONLY
    }
    match /fraud_signals/{sId} {
      allow read: if isAdmin();
      allow write: if isAdmin();
    }
  }
}
```

---

## 7. Android Client Security Configurations

Make sure to apply these secure declarations inside the Android app project.

### 7.1 Securing Network Requests with SSL Pinning
Add this inside `app/src/main/res/xml/network_security_config.xml` to prevent Man-In-The-Middle (MITM) package sniffer intercepts (like HTTP Canary or Charles Proxy) on production networks:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.greatshort.com</domain>
        <!-- Pin certificates for Production Server -->
        <pin-set expiration="2027-12-31">
            <pin digest="SHA-256">9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

Add reference to `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

### 7.2 Secure EncryptedSharedPreferences Token Storage
Do not store user session tokens or balance information in raw standard XML SharedPreferences, as rooted devices can easily dump and forge them. Use **AES-256 EncryptedSharedPreferences**:

```kotlin
package com.example.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {
    fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        "secure_user_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```
