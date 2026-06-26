import { collections, db } from '../config/firebase';

const sampleCategories = [
  { id: 'cat_drama', nameEnglish: 'Dramas / Serials', nameUrdu: 'ڈرامے', slug: 'dramas' },
  { id: 'cat_comedy', nameEnglish: 'Comedy Clips', nameUrdu: 'کامیڈی', slug: 'comedy' },
  { id: 'cat_romance', nameEnglish: 'Romance / Love', nameUrdu: 'محبت', slug: 'romance' },
  { id: 'cat_thriller', nameEnglish: 'Thriller / Action', nameUrdu: 'سنسنی خیز', slug: 'thriller' }
];

const sampleRewardRules = [
  { id: 'watch_pkr_per_sec', name: 'Watch Bonus PKR Per Second', value: 0.05, unit: 'pkr', isActive: true },
  { id: 'daily_check_in_bonus', name: 'Daily Check-In Reward', value: 10.00, unit: 'pkr', isActive: true },
  { id: 'streak_7day_bonus', name: '7-Day Continuous Streak Reward', value: 50.00, unit: 'pkr', isActive: true },
  { id: 'min_withdrawal_pkr', name: 'Minimum Payout Limit', value: 100.00, unit: 'pkr', isActive: true },
  { id: 'referral_referrer_reward', name: 'Referrer Reward', value: 50.00, unit: 'pkr', isActive: true },
  { id: 'referral_referee_reward', name: 'Referee Signup Reward', value: 20.00, unit: 'pkr', isActive: true }
];

const sampleVideos = [
  {
    id: 'vid_01',
    title: 'The Lost Love - Episode 1',
    description: 'A young heart seeks companionship in Lahore, only to face family disputes. (Urdu Drama)',
    videoUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
    thumbnailUrl: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500',
    durationSeconds: 15,
    categoryId: 'cat_drama',
    isPremium: false,
    isSoftDeleted: false,
    viewsCount: 1205,
    likesCount: 340,
    createdAt: FirebaseFirestore.Timestamp.now()
  },
  {
    id: 'vid_02',
    title: 'Laugh Out Loud - Funny Bloopers',
    description: 'Hilarious street interviews and epic prank fails from Karachi.',
    videoUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4',
    thumbnailUrl: 'https://images.unsplash.com/photo-1527224857830-43a7acc85260?w=500',
    durationSeconds: 15,
    categoryId: 'cat_comedy',
    isPremium: false,
    isSoftDeleted: false,
    viewsCount: 2390,
    likesCount: 984,
    createdAt: FirebaseFirestore.Timestamp.now()
  },
  {
    id: 'vid_03',
    title: 'Karachi Midnight Mystery',
    description: 'A suspenseful detective chase through the busy streets of Clifton.',
    videoUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4',
    thumbnailUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500',
    durationSeconds: 15,
    categoryId: 'cat_thriller',
    isPremium: true,
    isSoftDeleted: false,
    viewsCount: 85,
    likesCount: 42,
    createdAt: FirebaseFirestore.Timestamp.now()
  }
];

export async function seedDatabase() {
  console.log('🌱 Starting database seed injection...');
  const batch = db.batch();

  // Seed Categories
  for (const cat of sampleCategories) {
    const ref = collections.video_categories.doc(cat.id);
    batch.set(ref, {
      ...cat,
      createdAt: FirebaseFirestore.Timestamp.now()
    });
  }

  // Seed Reward Rules
  for (const rule of sampleRewardRules) {
    const ref = collections.reward_rules.doc(rule.id);
    batch.set(ref, {
      ...rule,
      updatedAt: FirebaseFirestore.Timestamp.now(),
      updatedBy: 'system_seed'
    });
  }

  // Seed Videos
  for (const video of sampleVideos) {
    const ref = collections.videos.doc(video.id);
    batch.set(ref, video);
  }

  try {
    await batch.commit();
    console.log('✅ Seeding complete! Database successfully populated with initial collections and settings.');
  } catch (error) {
    console.error('❌ Seeding failed:', error);
  }
}

// Execute if run directly
if (require.main === module) {
  seedDatabase();
}
