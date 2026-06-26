# Production Deployment Instructions - GreatShort Backend

Follow these steps to deploy the GreatShort Express API and secure Firestore database to production.

---

## 1. Environment Variables Configuration

Create a `.env` file in the root of your production deployment. Configure these parameters:

```env
PORT=8080
NODE_ENV=production

# Firebase Service Account JSON parsed as a string.
# In production, set this directly in your host's environment settings (e.g. GCP secret manager, Heroku config vars)
FIREBASE_SERVICE_ACCOUNT='{"type": "service_account", "project_id": "your-project", "private_key_id": "...", "private_key": "...", ...}'
FIREBASE_DATABASE_URL="https://your-project.firebaseio.com"
```

---

## 2. Setting Up Admin Custom Claims

Administrative APIs are locked behind Firebase Custom Claims. To promote a standard user account (e.g. `your-email@gmail.com` with UID `XYZ_123`) to an administrator:

1. Use Firebase Admin SDK on your server or via a local script.
2. Run this command in a Node.js console:

```javascript
const admin = require('firebase-admin');
admin.initializeApp();

admin.auth().setCustomUserClaims('XYZ_123', { role: 'admin' })
  .then(() => console.log('Successfully elevated user to ADMIN!'));
```

---

## 3. Production Firestore Security Rules

Deploy these security rules to Firebase Console (`firestore.rules`) to guarantee that malicious clients cannot manipulate ledger entries, balances, or bypass verification rules directly:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Helper: Is the user authenticated?
    function isAuthenticated() {
      return request.auth != null;
    }

    // Helper: Is the user retrieving their own record?
    function isOwner(userId) {
      return request.auth.uid == userId;
    }

    // Helper: Check if user is an Administrator using Custom Claims
    function isAdmin() {
      return isAuthenticated() && request.auth.token.role == 'admin';
    }

    // Users collection: readable by anyone logged in, writable only by owner or admin
    match /users/{userId} {
      allow read: if isAuthenticated();
      allow write: if isAdmin() || (isOwner(userId) && !request.resource.data.isBlocked);
    }

    // Wallet balances & Ledger records: READ-ONLY for owners. Writes strictly ADMIN-only (backend).
    match /wallet_accounts/{userId} {
      allow read: if isAuthenticated() && isOwner(userId);
      allow write: if isAdmin(); // Wallet changes must come through server-side ledger
    }

    match /wallet_transactions/{txId} {
      allow read: if isAuthenticated() && resource.data.userId == request.auth.uid;
      allow write: if isAdmin();
    }

    match /reward_ledger/{ledgerId} {
      allow read: if isAuthenticated() && resource.data.userId == request.auth.uid;
      allow write: if isAdmin();
    }

    // Videos & Categories: public read, admin write
    match /videos/{videoId} {
      allow read: if true;
      allow write: if isAdmin();
    }

    match /video_categories/{catId} {
      allow read: if true;
      allow write: if isAdmin();
    }

    // Watch Events & Fraud signals: writes are strictly administrative (backend) to prevent forged watch records
    match /watch_events/{eventId} {
      allow read: if isAuthenticated() && (resource.data.userId == request.auth.uid || isAdmin());
      allow write: if isAdmin(); // Server-side verify Watch events only
    }

    match /fraud_signals/{signalId} {
      allow read: if isAdmin();
      allow write: if isAdmin();
    }

    // Withdrawals: read-only for owner, admins process
    match /withdrawals/{withdrawalId} {
      allow read: if isAuthenticated() && (resource.data.userId == request.auth.uid || isAdmin());
      allow write: if isAdmin(); // Server processes payouts
    }

    // Notifications: owned by user
    match /notifications/{notifId} {
      allow read: if isAuthenticated() && resource.data.userId == request.auth.uid;
      allow write: if isAdmin();
      allow update: if isAuthenticated() && resource.data.userId == request.auth.uid && request.resource.data.isRead == true;
    }

    // Other collections fallback
    match /{document=**} {
      allow read, write: if isAdmin();
    }
  }
}
```

---

## 4. Deploying to Cloud Providers (e.g. Google Cloud Run, Heroku, or DigitalOcean)

### Standard Deployment:

1. Run standard TypeScript build command:
   ```bash
   npm install
   npm run build
   ```
2. Run database seed to inject initial rules and categories:
   ```bash
   npm run seed
   ```
3. Start the built server:
   ```bash
   npm start
   ```

### Dockerized Deployment:
Deploy with this production `Dockerfile` (optimized for lightweight runtime):

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json tsconfig.json ./
RUN npm install
COPY src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --only=production
COPY --from=builder /app/dist ./dist
EXPOSE 8080
ENV PORT=8080
ENV NODE_ENV=production
CMD ["node", "dist/index.js"]
```
