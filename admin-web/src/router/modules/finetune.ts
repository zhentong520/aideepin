import { RouteRecordRaw } from 'vue-router'
import { Layout } from '@/router/constant'
import { Model } from '@vicons/carbon'
import { renderIcon } from '@/utils/index'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/finetune',
    name: 'Finetune',
    redirect: '/finetune/dataset',
    component: Layout,
    meta: {
      title: 'route.finetuneManagement',
      icon: renderIcon(Model),
      sort: 6,
    },
    children: [
      {
        path: 'dataset',
        name: 'FinetuneDataset',
        meta: {
          title: 'route.finetuneDataset',
        },
        component: () => import('@/views/finetune/dataset.vue'),
      },
      {
        path: 'samplePool',
        name: 'FinetuneSamplePool',
        meta: {
          title: 'route.finetuneSamplePool',
        },
        component: () => import('@/views/finetune/samplePool.vue'),
      },
      {
        path: 'job',
        name: 'FinetuneJob',
        meta: {
          title: 'route.finetuneJob',
        },
        component: () => import('@/views/finetune/job.vue'),
      },
      {
        path: 'guide',
        name: 'FinetuneGuide',
        meta: {
          title: 'route.finetuneGuide',
        },
        component: () => import('@/views/finetune/guide.vue'),
      },
      {
        path: 'artifact',
        name: 'FinetuneArtifact',
        meta: {
          title: 'route.finetuneArtifact',
        },
        component: () => import('@/views/finetune/artifact.vue'),
      },
    ],
  },
]

export default routes
